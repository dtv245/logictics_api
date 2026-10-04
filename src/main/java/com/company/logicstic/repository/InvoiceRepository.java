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
            """)
    Page<Invoice> search(
            @Param("status") String status,
            @Param("type") String type,
            @Param("customerId") UUID customerId,
            Pageable pageable
    );

    java.util.List<Invoice> findAllByLoadId(UUID loadId);

    java.util.List<Invoice> findByCustomerId(UUID customerId);

    @Query("""
            SELECT i FROM Invoice i
            WHERE (:from IS NULL OR i.createdAt >= :from)
              AND (:to IS NULL OR i.createdAt <= :to)
            """)
    java.util.List<Invoice> findByPeriod(
            @Param("from") java.time.OffsetDateTime from,
            @Param("to") java.time.OffsetDateTime to
    );
}
