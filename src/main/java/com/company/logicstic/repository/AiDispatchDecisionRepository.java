package com.company.logicstic.repository;

import com.company.logicstic.entity.AiDispatchDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiDispatchDecisionRepository extends JpaRepository<AiDispatchDecision, UUID> {

    List<AiDispatchDecision> findBySessionId(UUID sessionId);

    Page<AiDispatchDecision> findPageBySessionId(UUID sessionId, Pageable pageable);

    List<AiDispatchDecision> findByTenantIdAndStatus(String tenantId, String status);

    Page<AiDispatchDecision> findPageByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    List<AiDispatchDecision> findByLoadIdAndStatus(UUID loadId, String status);

    List<AiDispatchDecision> findByLoadIdAndStatusAndTenantId(UUID loadId, String status, String tenantId);

    Optional<AiDispatchDecision> findByIdAndTenantId(UUID id, String tenantId);

    List<AiDispatchDecision> findBySessionIdAndStatus(UUID sessionId, String status);
}
