package com.company.logicstic.modules.role.repository;

import com.company.logicstic.modules.role.entity.TenantRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {

    boolean existsByNormalizedName(String normalizedName);

    Optional<TenantRole> findByNormalizedName(String normalizedName);

    Page<TenantRole> findAll(Pageable pageable);
}
