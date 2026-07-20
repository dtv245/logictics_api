package com.company.logicstic.modules.finance.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.logicstic.modules.finance.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"load", "customer", "employee"})
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

    /**
     * Finds an Invoice linked to a specific Load with a given status.
     * Used by LoadService.autoFlipInvoice() to flip Invoice from Draft to Issued on dispatch.
     */
    @Query("SELECT i FROM Invoice i WHERE i.load.id = :loadId AND i.status = :status")
    Optional<Invoice> findByLoadIdAndStatus(@Param("loadId") UUID loadId, @Param("status") String status);
}
