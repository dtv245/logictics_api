package com.company.logicstic.modules.role.dto.response;

import com.company.logicstic.modules.role.entity.TenantRole;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
    UUID id, String name, String displayName, String normalizedName, List<ClaimView> claims) {
  public record ClaimView(UUID id, String claimType, String claimValue) {}

  public static RoleResponse from(TenantRole role) {
    List<ClaimView> claims =
        role.getClaims() == null
            ? List.of()
            : role.getClaims().stream()
                .map(c -> new ClaimView(c.getId(), c.getClaimType(), c.getClaimValue()))
                .toList();
    return new RoleResponse(
        role.getId(), role.getName(), role.getDisplayName(), role.getNormalizedName(), claims);
  }
}
