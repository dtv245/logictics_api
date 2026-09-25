package com.company.logicstic.reporting;

import com.company.logicstic.fleet.truck.VehicleMileageReading;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads over odometer readings, the source of distance actually driven.
 *
 * <p>Nothing writes {@code vehicle_mileage_readings} yet, so every query here returns nothing today
 * and the metrics that depend on them report themselves unavailable. That is the point of having
 * the repository: the day a feed starts writing, total distance becomes a real measurement without
 * a code change, and until then the answer is "no readings" rather than a number borrowed from
 * loaded distance and quietly relabelled.
 *
 * <p>The spread is taken per truck and never summed across trucks. Odometer readings are cumulative
 * and each truck's counter starts wherever it starts, so adding two trucks' readings together
 * produces a figure with no meaning.
 */
public interface ReportingMileageRepository extends JpaRepository<VehicleMileageReading, UUID> {

  /**
   * The lowest and highest odometer reading per truck, over the window.
   *
   * <p>Returns trucks with a single reading too, rather than filtering them out in the query. A
   * single reading gives a spread of zero, and that zero must not be read as "did not move" — the
   * service excludes these trucks and counts them, which needs to see them first.
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.TruckDistance(
          r.truck.id, MIN(r.readingValue), MAX(r.readingValue), COUNT(r))
      FROM VehicleMileageReading r
      WHERE r.recordedAt >= :from AND r.recordedAt < :to
      GROUP BY r.truck.id
      """)
  List<TruckDistance> findReadingSpreadByTruck(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Readings in the window, for the completeness block. */
  @Query(
      """
      SELECT COUNT(r) FROM VehicleMileageReading r
      WHERE r.recordedAt >= :from AND r.recordedAt < :to
      """)
  long countInWindow(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
