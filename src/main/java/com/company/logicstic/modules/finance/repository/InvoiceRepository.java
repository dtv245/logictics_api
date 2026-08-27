package com.company.logicstic.modules.finance.repository;

import com.company.logicstic.modules.finance.entity.Invoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  @EntityGraph(attributePaths = {"load", "customer", "employee"})
  @Query(
      """
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
      Pageable pageable);

  /** Finds the single Invoice linked to a Load for the dispatch-owned domain transition. */
  @Query("SELECT i FROM Invoice i WHERE i.load.id = :loadId")
  Optional<Invoice> findByLoadId(@Param("loadId") UUID loadId);
}
