package com.company.logicstic.repository;

import com.company.logicstic.entity.AccidentReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Additive profile-facing access to the existing accident report table. */
public interface AccidentReportProfileRepository extends JpaRepository<AccidentReport, UUID> {
    @EntityGraph(attributePaths = {"driver", "truck", "trip"})
    Page<AccidentReport> findByDriver_IdAndStatusNot(UUID driverId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"driver", "truck", "trip"})
    Optional<AccidentReport> findByIdAndDriver_Id(UUID id, UUID driverId);

    @Query(value = """
            SELECT d.accident_report_id, d.id
            FROM public.documents d
            WHERE d.accident_report_id IN (:reportIds)
            ORDER BY d.accident_report_id, d.id
            """, nativeQuery = true)
    List<Object[]> findDocumentIdsByAccidentReportIds(@Param("reportIds") Collection<UUID> reportIds);
}
