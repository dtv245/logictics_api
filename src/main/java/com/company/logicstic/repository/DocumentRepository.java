package com.company.logicstic.repository;

import com.company.logicstic.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
            SELECT d FROM Document d
            WHERE (:type IS NULL OR d.type = :type)
              AND (:status IS NULL OR d.status = :status)
              AND (:loadId IS NULL OR d.load.id = :loadId)
              AND (:truckId IS NULL OR d.truck.id = :truckId)
              AND (:employeeId IS NULL OR d.employee.id = :employeeId)
            """)
    Page<Document> search(
            @Param("type") String type,
            @Param("status") String status,
            @Param("loadId") UUID loadId,
            @Param("truckId") UUID truckId,
            @Param("employeeId") UUID employeeId,
            Pageable pageable
    );
}
