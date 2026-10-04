package com.company.logicstic.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.entity.TripDriverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripDriverAssignmentRepository extends JpaRepository<TripDriverAssignment, UUID> {

    @Query("""
            SELECT a FROM TripDriverAssignment a JOIN FETCH a.trip t
            WHERE a.driver.id = :driverId
              AND a.effectiveFrom < :to
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :from)
            ORDER BY a.effectiveFrom
            """)
    List<TripDriverAssignment> findAssignmentsForDriverPeriod(@Param("driverId") UUID driverId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    List<TripDriverAssignment> findByTripIdOrderByEffectiveFromDesc(UUID tripId);

    List<TripDriverAssignment> findByTripIdAndEffectiveToIsNull(UUID tripId);

    @Query("""
            SELECT a FROM TripDriverAssignment a
            WHERE a.driver.id = :driverId
              AND a.effectiveFrom <= :atTime
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :atTime)
            """)
    List<TripDriverAssignment> findActiveAssignmentsForDriver(
            @Param("driverId") UUID driverId,
            @Param("atTime") OffsetDateTime atTime
    );

    @Query("""
            SELECT a FROM TripDriverAssignment a
            WHERE a.driver.id = :driverId
              AND a.trip.id != :excludeTripId
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :from)
              AND a.effectiveFrom <= :to
            """)
    List<TripDriverAssignment> findOverlappingAssignments(
            @Param("driverId") UUID driverId,
            @Param("excludeTripId") UUID excludeTripId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
