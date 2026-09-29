package com.company.logicstic.repository;

import com.company.logicstic.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("""
            SELECT t FROM Trip t
            WHERE (:search IS NULL
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
              AND (:truckId IS NULL OR t.truck.id = :truckId)
            """)
    Page<Trip> search(
            @Param("search") String search,
            @Param("status") String status,
            @Param("truckId") UUID truckId,
            Pageable pageable
    );
}
