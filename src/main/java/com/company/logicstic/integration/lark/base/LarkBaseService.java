package com.company.logicstic.integration.lark.base;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.company.logicstic.common.PaginationValidator;
import org.springframework.stereotype.Service;

/** Compatibility facade: every write now passes through persisted, declarative sync. */
@Service
public class LarkBaseService {
  private final LarkSyncService sync;
  private final LarkSyncMapping mapping;
  private final LarkBaseClient client;
  public LarkBaseService(LarkSyncService sync,LarkSyncMapping mapping,LarkBaseClient client) {
    this.sync=sync;this.mapping=mapping;this.client=client;
  }
  private LarkBaseRecord result(LarkSyncView view) {
    if(!"SYNCED".equals(view.state())) throw LarkSyncMapping.error(view.lastError()==null?"LARK_BASE_SYNC_FAILED":view.lastError());
    return LarkBaseRecord.of(view.recordId(),Map.of());
  }
  public LarkBaseRecord syncLoad(UUID id) { return result(sync.sync("load",id,false)); }
  public LarkBaseRecord syncDriver(UUID id) { return result(sync.sync("driver",id,false)); }
  public List<LarkBaseRecord> listSyncedLoads(Integer pageSize,String pageToken) {
    mapping.validate("load");
    int safePageSize = PaginationValidator.validatePageSize(pageSize);
    return client.listRecords(mapping.configured("LARK_BASE_APP_TOKEN"),mapping.table("load"),safePageSize,pageToken);
  }
}

