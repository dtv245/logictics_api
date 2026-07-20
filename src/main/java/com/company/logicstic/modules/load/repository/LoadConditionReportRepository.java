package com.company.logicstic.modules.load.repository;

import com.company.logicstic.modules.load.entity.LoadConditionReport;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoadConditionReportRepository extends JpaRepository<LoadConditionReport, UUID> {

  @EntityGraph(attributePaths = {"load", "inspectedBy"})
  @Query(
      """
            SELECT r FROM LoadConditionReport r
            WHERE (:loadId IS NULL OR r.load.id = :loadId)
              AND (:type IS NULL OR r.type = :type)
            """)
  Page<LoadConditionReport> search(
      @Param("loadId") UUID loadId, @Param("type") String type, Pageable pageable);
}
