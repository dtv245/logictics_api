package com.company.logicstic.integration.lark.base;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static com.company.logicstic.integration.lark.base.LarkSyncMapping.*;

@Service
public class LarkSyncService {
  private static final Logger log=LoggerFactory.getLogger(LarkSyncService.class);
  private final LarkSyncMapping mapping;
  private final LarkSyncStore store;
  private final LarkSyncGateway gateway;
  private final LoadRepository loads;
  private final EmployeeRepository employees;
  public LarkSyncService(LarkSyncMapping mapping,LarkSyncStore store,LarkSyncGateway gateway,
      LoadRepository loads,EmployeeRepository employees) {
    this.mapping=mapping;this.store=store;this.gateway=gateway;this.loads=loads;this.employees=employees;
  }
  private LarkSyncStore.Key key(String domain,UUID id) {
    return new LarkSyncStore.Key(TenantContext.getTenantId().orElse("single-tenant"),
        hash(mapping.setting("LARK_BASE_APP_TOKEN")),mapping.setting(mapping.domain(domain).tableEnv()),domain,id);
  }
  public LarkSyncView status(String domain,UUID id) { return store.view(key(domain,id),false); }
  public List<LarkSyncView> missing(String domain,int limit,int offset) {
    if(limit<1||limit>100||offset<0) throw error("LARK_SYNC_PAGE_INVALID");
    var k=key(domain,new UUID(0,0));return store.missing(k.tenant(),k.app(),k.table(),limit,offset);
  }
  private Map<String,Object> projection(String domain,UUID id,boolean linked) {
    return store.transaction.execute(s -> {
      if("load".equals(domain)) {
        var entity=loads.findById(id).orElse(null);
        if(entity==null) return null;
        return mapping.project(domain,entity,true,false);
      }
      var employee=employees.findById(id).orElse(null);
      if(employee==null) return null;
      if(employee.getEmployeeType() != EmployeeType.DRIVER)
        throw error("LARK_SYNC_NOT_A_DRIVER");
      if(!linked && !"ACTIVE".equalsIgnoreCase(employee.getStatus())) throw error("LARK_SYNC_DRIVER_NOT_ACTIVE");
      return mapping.project(domain,employee,true,false);
    });
  }
  public LarkSyncView sync(String domain,UUID id,boolean recreateMissing) {
    mapping.validate(domain);
    var k=key(domain,id);
    var existing=store.find(k,false);
    Map<String,Object> desired=projection(domain,id,existing.map(l->l.recordId()!=null).orElse(false));
    if(desired==null) {
      if(existing.isEmpty() || existing.get().recordId()==null) throw error("LARK_SYNC_SOURCE_NOT_FOUND");
      // Current entities have no soft-delete flag. Retain the previous projection and tombstone an absent source.
      desired=new TreeMap<>(read(existing.get().pending()));
      var lifecycle=mapping.domain(domain).fields().stream().filter(f->f.key().equals("lifecycle")).findFirst().orElseThrow();
      desired.put(mapping.name(lifecycle),"DELETED");
    }
    final Map<String,Object> payload=desired;
    if(existing.isEmpty()) {
      // No automatic adoption of old records. A manually specified backfill is required.
      var candidates=gateway.retry(()->gateway.find(domain,id));
      store.prepare(k,payload,mapping.fingerprint(domain),candidates.isEmpty()?"PENDING":"CONFLICT");
      if(!candidates.isEmpty()) {
        store.failure(k,"CONFLICT","LARK_BASE_BACKFILL_REQUIRED");return store.view(k,false);
      }
    }
    if(recreateMissing) store.resetMissing(k,payload,mapping.fingerprint(domain));
    return store.transaction.execute(s -> {
      var link=store.find(k,true).orElseThrow();
      if("MISSING".equals(link.state()) || ("CONFLICT".equals(link.state()) && link.recordId()==null)) return store.view(k,false);
      try {
        if(!mapping.fingerprint(domain).equals(link.mappingHash())) throw error("LARK_BASE_MAPPING_CHANGED");
        String wantedHash=hash(mapping.normalize(domain,payload));
        if(link.recordId()==null) {
          var pending=read(link.pending());
          if(!hash(mapping.normalize(domain,pending)).equals(wantedHash)) throw error("LARK_BASE_CREATE_INTENT_CHANGED");
          var recovered=gateway.retry(()->gateway.find(domain,id));
          if(recovered.size()>1) throw error("LARK_BASE_DUPLICATE_ID");
          LarkBaseRecord remote;
          if(recovered.isEmpty()) {
            var createFields=new TreeMap<String,Object>();
            pending.forEach((n,v)->{if(v!=null && !(v instanceof String text && text.isBlank())) createFields.put(n,v);});
            remote=gateway.retry(()->gateway.create(domain,createFields,link.token()));
          } else {
            remote=recovered.getFirst();
            if(mapping.unknown(domain,remote.fields()) || !wantedHash.equals(hash(mapping.normalize(domain,remote.fields()))))
              throw error("LARK_BASE_CREATE_RECOVERY_CONFLICT");
          }
          store.success(k,remote.recordId(),wantedHash,json(payload),mapping.fingerprint(domain));
          return store.view(k,false);
        }
        var remote=gateway.retry(()->gateway.get(domain,link.recordId()));
        if(mapping.unknown(domain,remote.fields())) throw error("LARK_BASE_UNOWNED_FIELDS");
        String actualHash=hash(mapping.normalize(domain,remote.fields()));
        if(wantedHash.equals(actualHash) && wantedHash.equals(link.hash()) && "SYNCED".equals(link.state()))
          return store.view(k,true);
        var previous=mapping.normalize(domain,remote.fields());
        if(!wantedHash.equals(actualHash)) {
          var overwritten=new TreeMap<String,Object>();
          previous.forEach((n,v)->{if(!Objects.equals(v,mapping.normalize(domain,payload).get(n))) overwritten.put(n,v);});
          store.audit(k,overwritten); // Tenant database audit, never application logs.
          gateway.retry(()->gateway.update(domain,link.recordId(),payload));
        }
        store.success(k,link.recordId(),wantedHash,json(payload),mapping.fingerprint(domain));
        return store.view(k,false);
      } catch(ApiException failure) {
        String code=failure.getCode();
        boolean missing=code.equals("LARK_BASE_RECORD_MISSING");
        boolean conflict=code.contains("CONFLICT")||code.contains("UNOWNED")||code.contains("MAPPING_CHANGED")||code.contains("INTENT_CHANGED")||code.contains("DUPLICATE");
        store.failure(k,missing?"MISSING":conflict?"CONFLICT":"ERROR",code);
        log.warn("Lark sync domain={} entity_id={} state={}",domain,id,missing?"MISSING":conflict?"CONFLICT":"ERROR");
        return store.view(k,false);
      }
    });
  }
  public record Backfill(UUID entityId,String recordId,boolean dryRun,boolean eligible,String reason) {}
  public Backfill backfill(String domain,UUID id,String recordId,boolean dryRun) {
    mapping.validate(domain);
    var k=key(domain,id);
    var before=store.find(k,false);
    if(before.isPresent() && before.get().recordId()!=null)
      return new Backfill(id,recordId,dryRun,false,"ALREADY_LINKED");
    var payload=projection(domain,id,false);
    if(payload==null) throw error("LARK_SYNC_SOURCE_NOT_FOUND");
    var candidate=gateway.retry(()->gateway.get(domain,recordId));
    Object remoteId=candidate.fields().get(mapping.identityField(domain));
    boolean eligible=(remoteId==null || remoteId.toString().isBlank() || remoteId.toString().equals(id.toString()))
        && !mapping.unknown(domain,candidate.fields());
    if(!dryRun && eligible) {
      store.prepare(k,payload,mapping.fingerprint(domain),"PENDING");
      store.transaction.executeWithoutResult(s -> {
        var current=store.find(k,true).orElseThrow();
        if(current.recordId()!=null) throw error("LARK_BASE_ALREADY_LINKED");
        store.success(k,recordId,"",json(payload),mapping.fingerprint(domain));
        store.failure(k,"PENDING","LARK_BASE_BACKFILL_PENDING_SYNC");
      });
    }
    return new Backfill(id,recordId,dryRun,eligible,eligible?"EXPLICIT_LINK_NO_PROVIDER_WRITE":"LARK_BASE_BACKFILL_CONFLICT");
  }
}

