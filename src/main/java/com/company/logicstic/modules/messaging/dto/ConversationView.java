package com.company.logicstic.modules.messaging.dto;

import com.company.logicstic.modules.messaging.entity.Conversation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationView(
    UUID id,
    String name,
    UUID loadId,
    Boolean isTenantChat,
    OffsetDateTime createdAt,
    OffsetDateTime lastMessageAt,
    List<UUID> participantIds) {
  public static ConversationView from(Conversation c) {
    return new ConversationView(
        c.getId(),
        c.getName(),
        c.getLoad() != null ? c.getLoad().getId() : null,
        c.getIsTenantChat(),
        c.getCreatedAt(),
        c.getLastMessageAt(),
        c.getParticipants().stream().map(p -> p.getEmployee().getId()).toList());
  }
}
