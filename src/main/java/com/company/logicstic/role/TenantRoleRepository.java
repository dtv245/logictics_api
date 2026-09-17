package com.company.logicstic.role;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {

  boolean existsByNormalizedName(String normalizedName);

  Optional<TenantRole> findByNormalizedName(String normalizedName);

  Page<TenantRole> findAll(Pageable pageable);
}
