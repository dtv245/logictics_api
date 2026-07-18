package com.company.logicstic.repository;

import com.company.logicstic.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("""
            SELECT i FROM Invoice i
            WHERE (:status IS NULL OR i.status = :status)
              AND (:type IS NULL OR i.type = :type)
              AND (:customerId IS NULL OR i.customer.id = :customerId)
              AND (:employeeId IS NULL OR i.employee.id = :employeeId)
            """)
    Page<Invoice> search(
            @Param("status") String status,
            @Param("type") String type,
            @Param("customerId") UUID customerId,
            @Param("employeeId") UUID employeeId,
            Pageable pageable
    );
}
