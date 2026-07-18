package com.company.logicstic.dto.role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank String name,
        String displayName,
        @NotNull @Valid List<ClaimRequest> claims
) {
    public record ClaimRequest(
            @NotBlank String claimType,
            @NotBlank String claimValue
    ) {}
}
