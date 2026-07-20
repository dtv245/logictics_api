package com.company.logicstic.modules.customer.repository;

import com.company.logicstic.modules.customer.entity.Customer;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  @EntityGraph(attributePaths = {})
  @Query(
      """
            SELECT c FROM Customer c
            WHERE (:search IS NULL
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR c.status = :status)
            """)
  Page<Customer> search(
      @Param("search") String search, @Param("status") String status, Pageable pageable);
}
