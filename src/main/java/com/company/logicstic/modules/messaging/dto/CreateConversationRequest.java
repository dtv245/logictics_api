package com.company.logicstic.modules.messaging.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record CreateConversationRequest(
    String name,
    UUID loadId,
    @NotNull Boolean isTenantChat,
    @NotEmpty Set<@NotNull UUID> participantIds) {}
