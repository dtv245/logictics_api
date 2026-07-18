package com.company.logicstic.dto.message;

import com.company.logicstic.entity.Conversation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConversationView(
        UUID id,
        String name,
        UUID loadId,
        Boolean isTenantChat,
        OffsetDateTime createdAt,
        OffsetDateTime lastMessageAt
) {
    public static ConversationView from(Conversation c) {
        return new ConversationView(
                c.getId(), c.getName(),
                c.getLoad() != null ? c.getLoad().getId() : null,
                c.getIsTenantChat(), c.getCreatedAt(), c.getLastMessageAt()
        );
    }
}
