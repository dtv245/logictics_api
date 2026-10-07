package com.company.logicstic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** The authenticated session boundary, without token or employee entity data. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CurrentUserResponse(
        @Schema(description = "Subject of the authenticated principal", requiredMode = Schema.RequiredMode.REQUIRED)
        String subject,
        @Schema(description = "Authenticated email, if present", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Authenticated tenant bound to this request", requiredMode = Schema.RequiredMode.REQUIRED)
        String tenantId,
        @Schema(description = "Current role authorities without the ROLE_ prefix; no semantic aliases", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> roles,
        @Schema(description = "Employee mapped within this tenant, or null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        UUID employeeId
) {
    public CurrentUserResponse {
        roles = List.copyOf(roles);
    }
}
