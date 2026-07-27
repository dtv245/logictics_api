package com.company.logicstic.modules.role.service.impl;

import com.company.logicstic.modules.role.dto.request.CreateRoleRequest;
import com.company.logicstic.modules.role.dto.response.RoleResponse;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.modules.role.entity.TenantRoleClaim;
import com.company.logicstic.modules.role.mapper.RoleMapper;
import com.company.logicstic.modules.role.repository.TenantRoleRepository;
import com.company.logicstic.modules.role.service.RoleService;
import com.company.logicstic.shared.common.CacheNames;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class RoleServiceImpl
    extends AbstractBaseService<TenantRole, RoleResponse, CreateRoleRequest>
    implements RoleService {

  private final TenantRoleRepository roleRepository;
  private final RoleMapper roleMapper;

  public RoleServiceImpl(TenantRoleRepository roleRepository, RoleMapper roleMapper) {
    super(roleRepository, roleMapper::toResponse, roleMapper::toEntity, roleMapper::updateEntity);
    this.roleRepository = roleRepository;
    this.roleMapper = roleMapper;
  }

  @Override
  protected String entityName() {
    return "Role";
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = CacheNames.ROLE, key = "#id")
  public RoleResponse getById(UUID id) {
    return super.getById(id);
  }

  // Also clears employee: EmployeeResponse copies roleName from the role.
  @Override
  @Transactional
  @CacheEvict(
      cacheNames = {CacheNames.ROLE, CacheNames.EMPLOYEE},
      allEntries = true)
  public RoleResponse create(CreateRoleRequest request) {
    return super.create(request);
  }

  @Override
  @Transactional
  @CacheEvict(
      cacheNames = {CacheNames.ROLE, CacheNames.EMPLOYEE},
      allEntries = true)
  public RoleResponse update(UUID id, CreateRoleRequest request) {
    return super.update(id, request);
  }

  @Override
  @Transactional
  @CacheEvict(
      cacheNames = {CacheNames.ROLE, CacheNames.EMPLOYEE},
      allEntries = true)
  public void delete(UUID id) {
    super.delete(id);
  }

  public PagedResponse<RoleResponse> list(int page, int pageSize) {
    var pageable = PageRequest.of(page - 1, pageSize, Sort.by("name").ascending());
    return PagedResponse.from(roleRepository.findAll(pageable).map(roleMapper::toResponse));
  }

  @Override
  protected void beforeCreate(TenantRole role, CreateRoleRequest request) {
    String normalizedName = request.name().toUpperCase();
    if (roleRepository.existsByNormalizedName(normalizedName)) {
      throw new ConflictException("Role with name '" + request.name() + "' already exists");
    }
    role.setNormalizedName(normalizedName);
    rebuildClaims(role, request);
  }

  @Override
  protected void beforeUpdate(TenantRole role, CreateRoleRequest request) {
    String normalizedName = request.name().toUpperCase();
    if (!role.getNormalizedName().equals(normalizedName)
        && roleRepository.existsByNormalizedName(normalizedName)) {
      throw new ConflictException("Role with name '" + request.name() + "' already exists");
    }
    role.setNormalizedName(normalizedName);
    role.getClaims().clear();
    rebuildClaims(role, request);
  }

  /**
   * Rebuilds the claims collection from the request. Kept in service (not mapper) because claims
   * require bidirectional parent-child linking.
   */
  private void rebuildClaims(TenantRole role, CreateRoleRequest request) {
    if (request.claims() != null) {
      request
          .claims()
          .forEach(
              c -> {
                TenantRoleClaim claim = new TenantRoleClaim();
                claim.setClaimType(c.claimType());
                claim.setClaimValue(c.claimValue());
                claim.setRole(role);
                role.getClaims().add(claim);
              });
    }
  }
}
