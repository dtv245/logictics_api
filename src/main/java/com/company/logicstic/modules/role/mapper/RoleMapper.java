package com.company.logicstic.modules.role.mapper;

import com.company.logicstic.modules.role.dto.request.CreateRoleRequest;
import com.company.logicstic.modules.role.dto.response.RoleResponse;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link TenantRole} entity and its DTOs.
 *
 * <p>The {@code claims} collection is <b>ignored</b> during entity mapping. The {@link
 * com.company.logicstic.service.RoleService} rebuilds the claims collection in-place to maintain
 * the parent-child relationship and cascade correctly.
 *
 * <p>The {@code toResponse} method maps the claims list via expression to produce {@link
 * RoleResponse.ClaimView} objects.
 */
@Mapper(config = MapperConfiguration.class)
public interface RoleMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "normalizedName", ignore = true)
  @Mapping(target = "claims", ignore = true)
  TenantRole toEntity(CreateRoleRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "normalizedName", ignore = true)
  @Mapping(target = "claims", ignore = true)
  void updateEntity(CreateRoleRequest req, @MappingTarget TenantRole role);

  /** Maps a role entity to its view, converting each claim to a {@link RoleResponse.ClaimView}. */
  @Mapping(
      target = "claims",
      expression =
          "java(role.getClaims() == null ? java.util.List.of() : role.getClaims().stream().map(c -> new com.company.logicstic.modules.role.dto.response.RoleResponse.ClaimView(c.getId(), c.getClaimType(), c.getClaimValue())).toList())")
  RoleResponse toResponse(TenantRole role);
}
