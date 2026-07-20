package com.company.logicstic.modules.role.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.role.dto.CreateRoleRequest;
import com.company.logicstic.modules.role.dto.RoleView;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.modules.role.entity.TenantRoleClaim;
import com.company.logicstic.modules.role.mapper.RoleMapper;
import com.company.logicstic.modules.role.repository.TenantRoleRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class RoleService extends AbstractBaseService<TenantRole, RoleView, CreateRoleRequest> {

    private final TenantRoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public RoleService(TenantRoleRepository roleRepository, RoleMapper roleMapper) {
        super(roleRepository, roleMapper::toView, roleMapper::toEntity, roleMapper::updateEntity);
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @Override
    protected String entityName() {
        return "Role";
    }

    public PagedResponse<RoleView> list(int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("name").ascending());
        return PagedResponse.from(roleRepository.findAll(pageable).map(roleMapper::toView));
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
        if (!role.getNormalizedName().equals(normalizedName) && roleRepository.existsByNormalizedName(normalizedName)) {
            throw new ConflictException("Role with name '" + request.name() + "' already exists");
        }
        role.setNormalizedName(normalizedName);
        role.getClaims().clear();
        rebuildClaims(role, request);
    }

    /**
     * Rebuilds the claims collection from the request.
     * Kept in service (not mapper) because claims require bidirectional parent-child linking.
     */
    private void rebuildClaims(TenantRole role, CreateRoleRequest request) {
        if (request.claims() != null) {
            request.claims().forEach(c -> {
                TenantRoleClaim claim = new TenantRoleClaim();
                claim.setClaimType(c.claimType());
                claim.setClaimValue(c.claimValue());
                claim.setRole(role);
                role.getClaims().add(claim);
            });
        }
    }
}