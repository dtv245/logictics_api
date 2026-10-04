package com.company.logicstic.integration.lark.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LarkBaseRecord(
    @JsonProperty("record_id") String recordId,
    Map<String, Object> fields) {

  public LarkBaseRecord {
    fields = fields == null ? new HashMap<>() : new HashMap<>(fields);
  }

  public static LarkBaseRecord of(Map<String, Object> fields) {
    return new LarkBaseRecord(null, fields);
  }

  public static LarkBaseRecord of(String recordId, Map<String, Object> fields) {
    return new LarkBaseRecord(recordId, fields);
  }

  public LarkBaseRecord withField(String key, Object value) {
    Map<String, Object> updated = new HashMap<>(this.fields);
    updated.put(key, value);
    return new LarkBaseRecord(this.recordId, updated);
  }
}
