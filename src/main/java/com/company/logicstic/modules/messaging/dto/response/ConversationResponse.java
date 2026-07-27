package com.company.logicstic.modules.messaging.dto.response;

import com.company.logicstic.modules.messaging.entity.Conversation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    String name,
    UUID loadId,
    Boolean isTenantChat,
    OffsetDateTime createdAt,
    OffsetDateTime lastMessageAt,
    List<UUID> participantIds) {
  public static ConversationResponse from(Conversation c) {
    return new ConversationResponse(
        c.getId(),
        c.getName(),
        c.getLoad() != null ? c.getLoad().getId() : null,
        c.getIsTenantChat(),
        c.getCreatedAt(),
        c.getLastMessageAt(),
        c.getParticipants().stream().map(p -> p.getEmployee().getId()).toList());
  }
}
