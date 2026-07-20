package com.company.logicstic.service;

import com.company.logicstic.dto.role.CreateRoleRequest;
import com.company.logicstic.dto.role.RoleView;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.entity.TenantRoleClaim;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.RoleMapper;
import com.company.logicstic.repository.TenantRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link RoleService} — verifies name conflict check,
 * claims rebuilding, and CRUD behavior after mapper refactor.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private TenantRoleRepository roleRepository;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private RoleService roleService;

    private CreateRoleRequest requestWithClaims() {
        return new CreateRoleRequest("driver", "Driver Role",
                List.of(
                        new CreateRoleRequest.ClaimRequest("permission", "load:read"),
                        new CreateRoleRequest.ClaimRequest("permission", "load:write")
                ));
    }

    @Test
    void create_duplicateNormalizedName_throwsConflictException() {
        CreateRoleRequest req = requestWithClaims();
        when(roleRepository.existsByNormalizedName("DRIVER")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("driver");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void create_setsNormalizedNameAndRebuildsClaimsCollection() {
        CreateRoleRequest req = requestWithClaims();
        when(roleRepository.existsByNormalizedName("DRIVER")).thenReturn(false);

        TenantRole entityWithClaims = new TenantRole();
        entityWithClaims.setName("driver");
        when(roleMapper.toEntity(req)).thenReturn(entityWithClaims);
        when(roleRepository.save(entityWithClaims)).thenReturn(entityWithClaims);
        when(roleMapper.toView(entityWithClaims)).thenReturn(mock(RoleView.class));

        roleService.create(req);

        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(roleRepository).save(captor.capture());
        TenantRole saved = captor.getValue();

        assertThat(saved.getNormalizedName()).isEqualTo("DRIVER");
        assertThat(saved.getClaims()).hasSize(2);
        assertThat(saved.getClaims().get(0).getClaimType()).isEqualTo("permission");
        assertThat(saved.getClaims().get(0).getRole()).isSameAs(saved);
    }

    @Test
    void update_existingRoleNameConflict_throwsConflictException() {
        UUID id = UUID.randomUUID();
        TenantRole existing = new TenantRole();
        existing.setId(id);
        existing.setNormalizedName("ADMIN");

        CreateRoleRequest req = new CreateRoleRequest("dispatcher", "Dispatcher",
                List.of());

        when(roleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(roleRepository.existsByNormalizedName("DISPATCHER")).thenReturn(true);

        assertThatThrownBy(() -> roleService.update(id, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("dispatcher");
    }

    @Test
    void update_clearsOldClaimsAndAddsNew() {
        UUID id = UUID.randomUUID();
        TenantRole existing = new TenantRole();
        existing.setId(id);
        existing.setNormalizedName("ADMIN");
        // pre-existing claim
        TenantRoleClaim oldClaim = new TenantRoleClaim();
        oldClaim.setClaimType("old");
        oldClaim.setClaimValue("old_val");
        existing.getClaims().add(oldClaim);

        CreateRoleRequest req = new CreateRoleRequest("admin", "Administrator",
                List.of(new CreateRoleRequest.ClaimRequest("perm", "new_val")));

        when(roleRepository.findById(id)).thenReturn(Optional.of(existing));
        // normalizedName "ADMIN" equals req name upper "ADMIN" → no conflict check triggered
        when(roleRepository.save(existing)).thenReturn(existing);
        when(roleMapper.toView(existing)).thenReturn(mock(RoleView.class));

        roleService.update(id, req);

        assertThat(existing.getClaims()).hasSize(1);
        assertThat(existing.getClaims().get(0).getClaimType()).isEqualTo("perm");
        assertThat(existing.getClaims().get(0).getClaimValue()).isEqualTo("new_val");
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(roleRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> roleService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
