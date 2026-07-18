package com.company.logicstic.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        String name,
        UUID loadId,
        @NotNull Boolean isTenantChat
) {}
