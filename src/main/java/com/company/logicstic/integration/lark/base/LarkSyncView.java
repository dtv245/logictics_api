package com.company.logicstic.integration.lark.base;

import java.time.OffsetDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties={"entityId","domain","state"})
public record LarkSyncView(UUID entityId, String domain, String state, String recordId,
    OffsetDateTime lastSyncedAt, String lastError, boolean unchanged) {
  public static LarkSyncView empty(UUID id, String domain) {
    return new LarkSyncView(id, domain, "NOT_SYNCED", null, null, null, false);
  }
}

