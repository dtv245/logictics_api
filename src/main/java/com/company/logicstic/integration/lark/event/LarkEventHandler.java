package com.company.logicstic.integration.lark.event;

import tools.jackson.databind.JsonNode;

public interface LarkEventHandler {
  String eventType();
  void handle(String eventId, JsonNode event);
}
