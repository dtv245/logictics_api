package com.company.logicstic.reporting;

import com.company.logicstic.customer.Customer;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads over customers for reporting.
 *
 * <p>{@code customers.status} is free text with exactly one observed value, {@code Active}, written
 * by the seeder. The active-customer count compares case-insensitively against values supplied by
 * the service rather than against a literal, and the raw distribution is reported alongside it so
 * that a status this deployment has never seen shows up as an unfamiliar row instead of being
 * quietly counted as inactive.
 */
public interface ReportingCustomerRepository extends JpaRepository<Customer, UUID> {

  /** Customers whose status matches one of the supplied lowercase values. */
  @Query("SELECT COUNT(c) FROM Customer c WHERE LOWER(c.status) IN :statuses")
  long countByStatusIn(@Param("statuses") Collection<String> statuses);

  /** The stored status distribution, unnormalised. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(c.status, COUNT(c))
      FROM Customer c
      GROUP BY c.status
      ORDER BY COUNT(c) DESC
      """)
  List<GroupCount> groupByStatus();
}
