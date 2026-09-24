package com.company.logicstic.reporting;

import com.company.logicstic.fleet.truck.Truck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Reads over the fleet for reporting.
 *
 * <p>No status predicate anywhere here, on purpose. {@code trucks.status} is plain text whose only
 * observed values are {@code Active} and {@code ACTIVE} — a seeder writes one casing and a mapper
 * test the other — so there is no dependable vocabulary to filter on. Fleet size is therefore every
 * truck on the books, and the raw status distribution is reported beside it so a reader can see
 * what the column actually contains rather than trusting a filter to have meant the right thing.
 */
public interface ReportingTruckRepository extends JpaRepository<Truck, UUID> {

  /** The stored status distribution, unnormalised. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(t.status, COUNT(t))
      FROM Truck t
      GROUP BY t.status
      ORDER BY COUNT(t) DESC
      """)
  List<GroupCount> groupByStatus();

  /** Trucks that carry a recorded odometer value, and so could contribute a distance. */
  @Query("""
      SELECT COUNT(t) FROM Truck t
      WHERE t.currentOdometer IS NOT NULL
      """)
  long countWithOdometer();
}
