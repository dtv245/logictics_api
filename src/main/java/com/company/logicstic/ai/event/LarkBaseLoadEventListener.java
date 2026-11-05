package com.company.logicstic.ai.event;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.integration.lark.base.LarkBaseProperties;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.integration.lark.event.LarkBaseRecordChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class LarkBaseLoadEventListener {

    private final LarkBaseProperties baseProperties;
    private final LarkProperties larkProperties;
    private final LoadEventDebouncer debouncer;
    private final JdbcTemplate jdbc;

    public LarkBaseLoadEventListener(
        LarkBaseProperties baseProperties,
        LarkProperties larkProperties,
        LoadEventDebouncer debouncer,
        DataSource dataSource
    ) {
        this.baseProperties = baseProperties;
        this.larkProperties = larkProperties;
        this.debouncer = debouncer;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @EventListener
    public void onLarkRecordChanged(LarkBaseRecordChangedEvent event) {
        String configuredTable = baseProperties.loadsTableId();
        if (!StringUtils.hasText(configuredTable) || !configuredTable.equals(event.tableId())) {
            return;
        }

        // Anti-loop: Check if the change only contains AI-owned fields
        Set<String> changedFieldIds = new HashSet<>();
        for (var f : event.afterValues()) {
            changedFieldIds.add(f.fieldId());
        }

        Set<String> aiFields = Set.of("ai_gaps_summary", "ai_dispatch_suggestion", "ai_score", "ai_status", "ai_recommendation");
        if (!changedFieldIds.isEmpty() && aiFields.containsAll(changedFieldIds)) {
            log.debug("LarkBaseLoadEventListener ignoring eventId={} because only AI-owned fields changed: {}", event.eventId(), changedFieldIds);
            return;
        }

        String tenantId = TenantContext.getTenantId().orElse(larkProperties.defaultTenantId());
        if (!StringUtils.hasText(tenantId)) {
            log.warn("Rejected LarkBaseRecordChangedEvent eventId={}: unable to determine tenant for tableId={}", event.eventId(), event.tableId());
            return;
        }

        // Resolve loadId from sync links
        Optional<UUID> resolvedLoadId = findLoadIdByRecordId(tenantId, event.tableId(), event.recordId());

        if (resolvedLoadId.isPresent()) {
            UUID loadId = resolvedLoadId.get();
            log.info("LarkBaseLoadEventListener resolved loadId={} from recordId={}", loadId, event.recordId());
            debouncer.onEvent(loadId, tenantId, "LARK_BASE", changedFieldIds);
        } else {
            log.debug("No mapped Load found for Lark recordId={}", event.recordId());
        }
    }

    private Optional<UUID> findLoadIdByRecordId(String tenantId, String tableId, String recordId) {
        try {
            return jdbc.query(
                "SELECT entity_id FROM lark_base_sync_links WHERE tenant_id=? AND table_id=? AND record_id=?",
                (rs, rowNum) -> rs.getObject("entity_id", UUID.class),
                tenantId, tableId, recordId
            ).stream().findFirst();
        } catch (Exception e) {
            log.warn("Failed to query lark_base_sync_links for recordId={}", recordId, e);
            return Optional.empty();
        }
    }
}
