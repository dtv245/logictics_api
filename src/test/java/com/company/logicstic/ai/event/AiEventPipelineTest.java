package com.company.logicstic.ai.event;

import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.integration.lark.base.LarkBaseProperties;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.integration.lark.event.LarkBaseRecordChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiEventPipelineTest {

    @Test
    @DisplayName("LoadEventDebouncer debounces multiple rapid events on same load into one dispatch")
    void debouncer_debouncesRapidEvents() throws InterruptedException {
        var properties = new AiProperties(true, true, false, 1, 5, 3, "Anthropic", "claude-haiku-4-5", "", "", 4096, 15);
        var publisher = mock(ApplicationEventPublisher.class);
        var debouncer = new LoadEventDebouncer(properties, publisher);

        UUID loadId = UUID.randomUUID();
        debouncer.onEvent(loadId, "tenant-1", "LARK_BASE", Set.of("field1"));
        debouncer.onEvent(loadId, "tenant-1", "LARK_BASE", Set.of("field2"));
        debouncer.onEvent(loadId, "tenant-1", "TMS", Set.of("field3"));

        // Flush immediately to verify merged dispatch
        debouncer.flush(loadId);

        verify(publisher, times(1)).publishEvent(any(LoadChangedInternalEvent.class));
    }

    @Test
    @DisplayName("LarkBaseLoadEventListener ignores events for non-load tables")
    void larkListener_ignoresNonLoadTables() {
        var baseProps = new LarkBaseProperties(true, "app-tok", "tbl_loads_correct", "tbl_drivers");
        var larkProps = new LarkProperties(false, null, null, null, "a-secure-custom-32-byte-secret-for-test-suite", null, null, null, null, "tenant-1", null, null);
        var debouncer = mock(LoadEventDebouncer.class);
        var dataSource = mock(DataSource.class);

        var listener = new LarkBaseLoadEventListener(baseProps, larkProps, debouncer, dataSource);

        var event = new LarkBaseRecordChangedEvent(this, "evt-1", "tbl_other_table", "rec-1", "record_edited", List.of());
        listener.onLarkRecordChanged(event);

        verifyNoInteractions(debouncer);
    }

    @Test
    @DisplayName("LarkBaseLoadEventListener resolves loadId from sync links and feeds debouncer")
    void larkListener_resolvesLoadIdAndFeedsDebouncer() throws Exception {
        var baseProps = new LarkBaseProperties(true, "app-tok", "tbl_loads_correct", "tbl_drivers");
        var larkProps = new LarkProperties(false, null, null, null, "a-secure-custom-32-byte-secret-for-test-suite", null, null, null, null, "tenant-1", null, null);
        var debouncer = mock(LoadEventDebouncer.class);
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var resultSet = mock(ResultSet.class);

        UUID expectedLoadId = UUID.randomUUID();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(eq("entity_id"), eq(UUID.class))).thenReturn(expectedLoadId);

        var listener = new LarkBaseLoadEventListener(baseProps, larkProps, debouncer, dataSource);

        var event = new LarkBaseRecordChangedEvent(this, "evt-1", "tbl_loads_correct", "rec-1", "record_edited",
            List.of(new LarkBaseRecordChangedEvent.FieldValue("fld_status", "Active")),
            List.of(new LarkBaseRecordChangedEvent.FieldValue("fld_status", "Draft")),
            0, 1L);

        listener.onLarkRecordChanged(event);

        verify(debouncer).onEvent(eq(expectedLoadId), eq("tenant-1"), eq("LARK_BASE"), anySet());
    }
}
