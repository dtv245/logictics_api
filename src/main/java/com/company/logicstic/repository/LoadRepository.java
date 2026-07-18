package com.company.logicstic.repository;

import com.company.logicstic.entity.Load;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LoadRepository extends JpaRepository<Load, UUID> {

    @Query("""
            SELECT l FROM Load l
            WHERE (:search IS NULL
                   OR LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(l.externalBrokerReference) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR l.status = :status)
              AND (:customerId IS NULL OR l.customer.id = :customerId)
              AND (:truckId IS NULL OR l.assignedTruck.id = :truckId)
              AND (:dispatcherId IS NULL OR l.assignedDispatcher.id = :dispatcherId)
            """)
    Page<Load> search(
            @Param("search") String search,
            @Param("status") String status,
            @Param("customerId") UUID customerId,
            @Param("truckId") UUID truckId,
            @Param("dispatcherId") UUID dispatcherId,
            Pageable pageable
    );
}
