package com.company.logicstic.integration.lark.base;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class LarkSyncStore {
  public record Key(String tenant, String app, String table, String domain, UUID id) {}
  public record Link(String recordId, UUID token, String pending, String hash, String mappingHash,
      String state, OffsetDateTime lastSynced, String error) {}
  final JdbcTemplate jdbc;
  final TransactionTemplate transaction;
  public LarkSyncStore(DataSource source, PlatformTransactionManager manager) {
    jdbc = new JdbcTemplate(source);
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }
  Object[] args(Key k) { return new Object[]{k.tenant(),k.app(),k.table(),k.domain(),k.id()}; }
  static final String WHERE = " tenant_id=? AND app_scope=? AND table_id=? AND domain=? AND entity_id=?";
  public Optional<Link> find(Key k, boolean lock) {
    return jdbc.query("SELECT * FROM lark_base_sync_links WHERE" + WHERE + (lock ? " FOR UPDATE" : ""),
      (r,n) -> new Link(r.getString("record_id"),r.getObject("client_token",UUID.class),
          r.getString("pending_payload"),r.getString("content_hash"),r.getString("mapping_hash"),
          r.getString("state"),r.getObject("last_synced_at",OffsetDateTime.class),r.getString("last_error")), args(k)).stream().findFirst();
  }
  public void prepare(Key k, Map<String,Object> payload, String mappingHash, String state) {
    transaction.executeWithoutResult(s -> jdbc.update("""
        INSERT INTO lark_base_sync_links(tenant_id,app_scope,table_id,domain,entity_id,client_token,pending_payload,mapping_hash,state)
        VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
        """, k.tenant(),k.app(),k.table(),k.domain(),k.id(),UUID.randomUUID(),LarkSyncMapping.json(payload),mappingHash,state));
  }
  public void failure(Key k,String state,String error) {
    var p = new ArrayList<Object>(List.of(state,error)); p.addAll(Arrays.asList(args(k)));
    jdbc.update("UPDATE lark_base_sync_links SET state=?,last_error=? WHERE"+WHERE,p.toArray());
  }
  public void success(Key k,String recordId,String hash,String payload,String mappingHash) {
    var p = new ArrayList<Object>(List.of(recordId,hash,payload,mappingHash)); p.addAll(Arrays.asList(args(k)));
    jdbc.update("UPDATE lark_base_sync_links SET record_id=?,content_hash=?,pending_payload=?,mapping_hash=?,state='SYNCED',last_error=NULL,last_synced_at=CURRENT_TIMESTAMP WHERE"+WHERE,p.toArray());
  }
  public void resetMissing(Key k, Map<String,Object> payload,String mappingHash) {
    transaction.executeWithoutResult(s -> {
      var p = new ArrayList<Object>(List.of(UUID.randomUUID(),LarkSyncMapping.json(payload),mappingHash));
      p.addAll(Arrays.asList(args(k)));
      jdbc.update("UPDATE lark_base_sync_links SET record_id=NULL,client_token=?,pending_payload=?,mapping_hash=?,content_hash=NULL,state='PENDING',last_error=NULL WHERE"+WHERE+" AND state='MISSING'",p.toArray());
    });
  }
  public void audit(Key k,Map<String,Object> previous) {
    if (!previous.isEmpty()) jdbc.update("INSERT INTO lark_base_sync_audit(id,tenant_id,domain,entity_id,previous_fields) VALUES (?,?,?,?,?)",
        UUID.randomUUID(),k.tenant(),k.domain(),k.id(),LarkSyncMapping.json(previous));
  }
  public List<LarkSyncView> missing(String tenant,String app,String table,int limit,int offset) {
    return jdbc.query("SELECT * FROM lark_base_sync_links WHERE tenant_id=? AND app_scope=? AND table_id=? AND state='MISSING' ORDER BY entity_id LIMIT ? OFFSET ?",
        (r,n)->new LarkSyncView(r.getObject("entity_id",UUID.class),r.getString("domain"),r.getString("state"),
            r.getString("record_id"),r.getObject("last_synced_at",OffsetDateTime.class),r.getString("last_error"),false),
        tenant,app,table,limit,offset);
  }
  public LarkSyncView view(Key k,boolean unchanged) {
    return find(k,false).map(l->new LarkSyncView(k.id(),k.domain(),l.state(),l.recordId(),l.lastSynced(),l.error(),unchanged))
        .orElseGet(()->LarkSyncView.empty(k.id(),k.domain()));
  }
}

