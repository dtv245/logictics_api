package com.company.logicstic.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID> {

    @Query("""
            SELECT m FROM MaintenanceRecord m
            WHERE (:truckId IS NULL OR m.truck.id = :truckId)
              AND (:from IS NULL OR m.serviceDate >= :from)
              AND (:to IS NULL OR m.serviceDate <= :to)
            """)
    List<MaintenanceRecord> findByFilters(
            @Param("truckId") UUID truckId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
