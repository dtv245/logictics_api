package com.company.logicstic.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.LoadException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoadExceptionRepository extends JpaRepository<LoadException, UUID> {

    List<LoadException> findByLoadId(UUID loadId);

    @Query("""
            SELECT e FROM LoadException e
            WHERE (:from IS NULL OR e.occurredAt >= :from)
              AND (:to IS NULL OR e.occurredAt <= :to)
            """)
    List<LoadException> findByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
