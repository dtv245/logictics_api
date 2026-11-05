package com.company.logicstic.integration.lark.event;

import com.company.logicstic.integration.lark.base.LarkBaseProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LarkBaseRecordChangedHandlerTest {
  @Test void publishesEachSupportedChangeAndKeepsFieldValueJsonStringsUnparsed() {
    var published = new ArrayList<Object>();
    var handler = new LarkBaseRecordChangedHandler(new LarkBaseProperties(true, "base-fixture", "", ""),
        published::add);
    var payload = JSON.createObjectNode().put("file_token", "base-fixture").put("table_id", "table-one");
    var actions = payload.putArray("action_list");
    for (String action : List.of("record_added", "record_edited", "record_deleted")) {
      var change = actions.addObject().put("record_id", "record-one").put("action", action);
      change.putArray("after_value").addObject().put("field_id", "field-one")
          .put("field_value", "{\"text\":\"giữ nguyên\"}");
    }
    handler.handle("event-one", payload);
    assertEquals(3, published.size());
    for (Object value : published) {
      var change = assertInstanceOf(LarkBaseRecordChangedEvent.class, value);
      assertEquals("event-one", change.eventId());
      assertEquals("table-one", change.tableId());
      assertEquals("record-one", change.recordId());
      assertEquals(List.of("{\"text\":\"giữ nguyên\"}"), change.fieldValues());
      assertThrows(UnsupportedOperationException.class, () -> change.fieldValues().add("changed"));
    }
  }

  @Test void ignoresAnotherBaseMissingOrMalformedProviderKeysAndUnsupportedActions() {
    var published = new ArrayList<Object>();
    var handler = new LarkBaseRecordChangedHandler(new LarkBaseProperties(true, "base-fixture", "", ""), published::add);
    for (String raw : List.of("{}", "[]", "null", "{\"file_token\":42}",
        "{\"file_token\":\"other-base\",\"table_id\":\"table-one\",\"action_list\":[]}",
        "{\"file_token\":\"base-fixture\",\"table_id\":\"table-one\",\"action_list\":{}}",
        "{\"file_token\":\"base-fixture\",\"table_id\":\"table-one\",\"action_list\":[null,1,{}, {\"record_id\":\"r\",\"action\":\"unknown\"}]}")) {
      assertDoesNotThrow(() -> handler.handle("event-one", JSON.readTree(raw)));
    }
    assertTrue(published.isEmpty());
  }
}
