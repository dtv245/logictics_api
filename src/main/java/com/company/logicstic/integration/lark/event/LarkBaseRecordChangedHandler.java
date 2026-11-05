package com.company.logicstic.integration.lark.event;

import com.company.logicstic.integration.lark.base.LarkBaseProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

@Component
public class LarkBaseRecordChangedHandler implements LarkEventHandler {
  public static final String EVENT_TYPE = "drive.file.bitable_record_changed_v1";
  private static final Logger log = LoggerFactory.getLogger(LarkBaseRecordChangedHandler.class);
  private static final Set<String> ACTIONS = Set.of("record_added", "record_deleted", "record_edited");
  private final LarkBaseProperties properties;
  private final ApplicationEventPublisher publisher;

  public LarkBaseRecordChangedHandler(LarkBaseProperties properties, ApplicationEventPublisher publisher) {
    this.properties = properties;
    this.publisher = publisher;
  }

  @Override
  public String eventType() { return EVENT_TYPE; }

  @Override
  public void handle(String eventId, JsonNode event) {
    String tableId = LarkEventSecurity.text(event, "table_id");
    if (!StringUtils.hasText(properties.appToken())
        || !properties.appToken().equals(LarkEventSecurity.text(event, "file_token"))) {
      log.debug("Lark event ignored event_id={} event_type={} table_id={} changes=0",
          LarkEventDispatcher.safeLog(eventId), EVENT_TYPE, LarkEventDispatcher.safeLog(tableId));
      return;
    }
    // Verified against Lark's official event schema. Actual app field IDs and
    // their business mapping still require a sanitized live payload.
    JsonNode actions = event.path("action_list");
    Long revision = event.path("revision").isIntegralNumber() ? event.path("revision").longValue() : null;
    int changes = 0;
    if (StringUtils.hasText(tableId) && actions.isArray()) {
      for (int index = 0; index < actions.size(); index++) {
        JsonNode change = actions.get(index);
        String action = LarkEventSecurity.text(change, "action");
        String recordId = LarkEventSecurity.text(change, "record_id");
        if (action == null || !ACTIONS.contains(action) || !StringUtils.hasText(recordId)) continue;
        publisher.publishEvent(new LarkBaseRecordChangedEvent(this, eventId, tableId, recordId, action,
            fields(change.path("before_value")), fields(change.path("after_value")), index, revision));
        changes++;
      }
    }
    log.info("Lark event dispatched event_id={} event_type={} table_id={} changes={}",
        LarkEventDispatcher.safeLog(eventId), EVENT_TYPE, LarkEventDispatcher.safeLog(tableId), changes);
  }

  private static List<LarkBaseRecordChangedEvent.FieldValue> fields(JsonNode node) {
    var values = new ArrayList<LarkBaseRecordChangedEvent.FieldValue>();
    if (!node.isArray()) return values;
    for (JsonNode field : node) {
      String fieldId = LarkEventSecurity.text(field, "field_id");
      String value = LarkEventSecurity.text(field, "field_value");
      if (StringUtils.hasText(fieldId) && value != null)
        values.add(new LarkBaseRecordChangedEvent.FieldValue(fieldId, value));
    }
    return values;
  }
}
