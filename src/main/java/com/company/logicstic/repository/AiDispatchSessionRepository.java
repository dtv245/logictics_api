package com.company.logicstic.repository;

import com.company.logicstic.entity.AiDispatchSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiDispatchSessionRepository extends JpaRepository<AiDispatchSession, UUID> {

    Page<AiDispatchSession> findByTenantId(String tenantId, Pageable pageable);

    Page<AiDispatchSession> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Optional<AiDispatchSession> findByIdAndTenantId(UUID id, String tenantId);
}
