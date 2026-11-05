package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverOperationalStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverOperationalStatusRepository extends JpaRepository<DriverOperationalStatus, UUID> {
    Optional<DriverOperationalStatus> findByEmployee_Id(UUID employeeId);

    /** Returns at most two active assignment trip IDs so the caller can avoid guessing on overlapping history. */
    @Query(value = """
            SELECT trip_id
            FROM public.trip_driver_assignments
            WHERE driver_id = :driverId
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to >= :asOf)
            GROUP BY trip_id
            ORDER BY MAX(effective_from) DESC, trip_id
            LIMIT 2
            """, nativeQuery = true)
    List<UUID> findCurrentTripIds(@Param("driverId") UUID driverId, @Param("asOf") OffsetDateTime asOf);
}
