package com.company.logicstic.repository;

import com.company.logicstic.entity.AiRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    @Query("""
        SELECT r FROM AiRun r
        WHERE r.tenantId = :tenantId
          AND (:loadId IS NULL OR r.loadId = :loadId)
          AND (:runType IS NULL OR r.runType = :runType)
          AND (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
    """)
    Page<AiRun> searchRuns(
        @Param("tenantId") String tenantId,
        @Param("loadId") UUID loadId,
        @Param("runType") String runType,
        @Param("status") String status,
        Pageable pageable
    );
}
