package com.company.logicstic.modules.finance.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.logicstic.modules.finance.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"invoice"})
    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:invoiceId IS NULL OR p.invoice.id = :invoiceId)
            """)
    Page<Payment> search(
            @Param("status") String status,
            @Param("invoiceId") UUID invoiceId,
            Pageable pageable
    );
}