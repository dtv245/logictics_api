package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.role.CreateRoleRequest;
import com.company.logicstic.dto.role.RoleView;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.entity.TenantRoleClaim;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.TenantRoleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final TenantRoleRepository roleRepository;

    public RoleService(TenantRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public PagedResponse<RoleView> list(int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("name").ascending());
        return PagedResponse.from(roleRepository.findAll(pageable).map(RoleView::from));
    }

    public RoleView getById(UUID id) {
        return roleRepository.findById(id)
                .map(RoleView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }

    @Transactional
    public RoleView create(CreateRoleRequest request) {
        String normalizedName = request.name().toUpperCase();
        if (roleRepository.existsByNormalizedName(normalizedName)) {
            throw new ConflictException("Role with name '" + request.name() + "' already exists");
        }

        TenantRole role = new TenantRole();
        role.setName(request.name());
        role.setDisplayName(request.displayName());
        role.setNormalizedName(normalizedName);

        if (request.claims() != null) {
            request.claims().forEach(c -> {
                TenantRoleClaim claim = new TenantRoleClaim();
                claim.setClaimType(c.claimType());
                claim.setClaimValue(c.claimValue());
                claim.setRole(role);
                role.getClaims().add(claim);
            });
        }

        return RoleView.from(roleRepository.save(role));
    }

    @Transactional
    public RoleView update(UUID id, CreateRoleRequest request) {
        TenantRole role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

        String normalizedName = request.name().toUpperCase();
        if (!role.getNormalizedName().equals(normalizedName) && roleRepository.existsByNormalizedName(normalizedName)) {
            throw new ConflictException("Role with name '" + request.name() + "' already exists");
        }

        role.setName(request.name());
        role.setDisplayName(request.displayName());
        role.setNormalizedName(normalizedName);

        role.getClaims().clear();
        if (request.claims() != null) {
            request.claims().forEach(c -> {
                TenantRoleClaim claim = new TenantRoleClaim();
                claim.setClaimType(c.claimType());
                claim.setClaimValue(c.claimValue());
                claim.setRole(role);
                role.getClaims().add(claim);
            });
        }

        return RoleView.from(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Role not found: " + id);
        }
        roleRepository.deleteById(id);
    }
}
