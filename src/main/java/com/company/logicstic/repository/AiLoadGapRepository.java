package com.company.logicstic.repository;

import com.company.logicstic.entity.AiLoadGap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiLoadGapRepository extends JpaRepository<AiLoadGap, UUID> {

    List<AiLoadGap> findByTenantIdAndLoadId(String tenantId, UUID loadId);

    @Query("""
        select g from AiLoadGap g
        where g.tenantId = :tenantId and g.load.id = :loadId
          and (:severity is null or g.severity = :severity)
          and (:status is null or g.status = :status)
        """)
    Page<AiLoadGap> findGapPage(@Param("tenantId") String tenantId, @Param("loadId") UUID loadId,
                                @Param("severity") String severity, @Param("status") String status,
                                Pageable pageable);

    List<AiLoadGap> findByTenantIdAndLoadIdAndStatus(String tenantId, UUID loadId, String status);

    Optional<AiLoadGap> findByTenantIdAndLoadIdAndRuleCode(String tenantId, UUID loadId, String ruleCode);

    long countByTenantIdAndLoadIdAndSeverityAndStatus(String tenantId, UUID loadId, String severity, String status);

    boolean existsByTenantIdAndLoadIdAndSeverityAndStatus(String tenantId, UUID loadId, String severity, String status);

    List<AiLoadGap> findByTenantIdAndStatus(String tenantId, String status);
}
