package com.company.logicstic.integration.lark.event;

import java.util.List;
import org.springframework.context.ApplicationEvent;

/** Raw field_value strings are deliberately not interpreted as business data. */
public final class LarkBaseRecordChangedEvent extends ApplicationEvent {
  private final String eventId;
  private final String tableId;
  private final String recordId;
  private final String action;
  private final List<String> fieldValues;
  private final List<FieldValue> beforeValues;
  private final List<FieldValue> afterValues;
  private final int changeIndex;
  private final Long revision;

  public LarkBaseRecordChangedEvent(Object source, String eventId, String tableId,
      String recordId, String action, List<String> fieldValues) {
    super(source);
    this.eventId = eventId;
    this.tableId = tableId;
    this.recordId = recordId;
    this.action = action;
    this.fieldValues = List.copyOf(fieldValues);
    this.beforeValues = List.of();
    this.afterValues = List.of();
    this.changeIndex = 0;
    this.revision = null;
  }

  public LarkBaseRecordChangedEvent(Object source, String eventId, String tableId, String recordId,
      String action, List<FieldValue> beforeValues, List<FieldValue> afterValues, int changeIndex, Long revision) {
    super(source);
    this.eventId = eventId;
    this.tableId = tableId;
    this.recordId = recordId;
    this.action = action;
    this.beforeValues = List.copyOf(beforeValues);
    this.afterValues = List.copyOf(afterValues);
    this.fieldValues = java.util.stream.Stream.concat(beforeValues.stream(), afterValues.stream())
        .map(FieldValue::fieldValue).toList();
    this.changeIndex = changeIndex;
    this.revision = revision;
  }

  public String eventId() { return eventId; }
  public String tableId() { return tableId; }
  public String recordId() { return recordId; }
  public String action() { return action; }
  public List<String> fieldValues() { return fieldValues; }
  public List<FieldValue> beforeValues() { return beforeValues; }
  public List<FieldValue> afterValues() { return afterValues; }
  /** Stable consumer identity is (eventId, changeIndex); retries are at least once. */
  public int changeIndex() { return changeIndex; }
  public Long revision() { return revision; }

  public record FieldValue(String fieldId, String fieldValue) {
    @Override public String toString() { return "LarkFieldValue[redacted]"; }
  }
}
