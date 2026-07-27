package com.company.logicstic.modules.messaging.dto.response;

import com.company.logicstic.modules.messaging.entity.Message;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderId,
    String senderName,
    String content,
    OffsetDateTime sentAt,
    Boolean isDeleted) {
  public static MessageResponse from(Message m) {
    String senderName =
        m.getSender() != null
            ? m.getSender().getFirstName() + " " + m.getSender().getLastName()
            : null;
    return new MessageResponse(
        m.getId(),
        m.getConversation().getId(),
        m.getSender() != null ? m.getSender().getId() : null,
        senderName,
        m.getContent(),
        m.getSentAt(),
        m.getIsDeleted());
  }
}
