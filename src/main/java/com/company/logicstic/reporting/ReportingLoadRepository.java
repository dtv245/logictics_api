package com.company.logicstic.reporting;

import com.company.logicstic.load.core.Load;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate reads over loads, for reporting only.
 *
 * <p>Loads carry no currency of their own that a revenue figure could use — {@code
 * Load.deliveryCostCurrency} exists, but that column is the invoice subtotal's source, not a cost —
 * so nothing here filters by currency. Revenue comes from invoices; distance and punctuality come
 * from here.
 *
 * <p>The status predicate is expected to carry lowercase values from {@link
 * com.company.logicstic.load.core.LoadStatus#dbValue()}, because {@code loads.status} is plain text
 * and {@code DataSeeder} does not always write the lowercase form.
 *
 * <p>Nothing here totals {@code Load.deliveryCostAmount}. That column is revenue — the seeder
 * builds each invoice's subtotal from it — so treating it as a cost would double-count the same
 * money.
 */
public interface ReportingLoadRepository extends JpaRepository<Load, UUID> {

  /** Loaded distance per calendar month, UTC, over the window, by delivery date. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.LoadMonthlyMiles(
          YEAR(l.deliveredAt), MONTH(l.deliveredAt), SUM(l.distance), COUNT(l))
      FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.deliveredAt >= :from AND l.deliveredAt < :to
      GROUP BY YEAR(l.deliveredAt), MONTH(l.deliveredAt)
      """)
  List<LoadMonthlyMiles> findMonthlyMiles(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /**
   * Distance and punctuality per customer, over the window.
   *
   * <p>{@code comparableCount} counts only loads carrying both dates, and both the numerator and
   * the denominator of on-time performance are drawn from it. A load with no requested delivery
   * date cannot have been late, so counting it as on time would inflate the percentage with rows
   * that were never measured.
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.LoadCustomerStats(
          l.customer.id,
          SUM(l.distance),
          COUNT(l),
          SUM(CASE WHEN l.deliveredAt IS NOT NULL AND l.requestedDeliveryDate IS NOT NULL
                   THEN 1 ELSE 0 END),
          SUM(CASE WHEN l.deliveredAt IS NOT NULL AND l.requestedDeliveryDate IS NOT NULL
                    AND l.deliveredAt <= l.requestedDeliveryDate
                   THEN 1 ELSE 0 END))
      FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.deliveredAt >= :from AND l.deliveredAt < :to
      GROUP BY l.customer.id
      """)
  List<LoadCustomerStats> findCustomerStats(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /**
   * Fleet-wide on-time counts over the window.
   *
   * <p>{@code COALESCE} on the summed case is load-bearing. This query has no {@code GROUP BY}, so
   * an empty window yields one row whose {@code SUM} is SQL {@code NULL}; without the coalesce the
   * projection's {@code long} would fail to unbox and an empty month would throw instead of
   * reporting "no comparable loads".
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.LoadOnTimeCounts(
          COUNT(l),
          COALESCE(SUM(CASE WHEN l.deliveredAt IS NOT NULL AND l.requestedDeliveryDate IS NOT NULL
                             AND l.deliveredAt <= l.requestedDeliveryDate
                            THEN 1 ELSE 0 END), 0))
      FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.deliveredAt >= :from AND l.deliveredAt < :to
        AND l.deliveredAt IS NOT NULL
        AND l.requestedDeliveryDate IS NOT NULL
      """)
  LoadOnTimeCounts countOnTime(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Fleet-wide DIFOT counts (Delivery In Full On Time) over the window. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.LoadDifotCounts(
          COUNT(l),
          COALESCE(SUM(CASE WHEN l.deliveredAt IS NOT NULL AND l.requestedDeliveryDate IS NOT NULL
                             AND l.deliveredAt <= l.requestedDeliveryDate
                             AND NOT EXISTS (
                               SELECT 1 FROM LoadConditionReport r
                               JOIN r.defects d
                               WHERE r.load = l
                             )
                             AND NOT EXISTS (
                               SELECT 1 FROM LoadExceptionEvent e
                               WHERE e.load = l AND e.resolvedAt IS NULL
                             )
                            THEN 1 ELSE 0 END), 0))
      FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.deliveredAt >= :from AND l.deliveredAt < :to
        AND l.deliveredAt IS NOT NULL
        AND l.requestedDeliveryDate IS NOT NULL
      """)
  LoadDifotCounts countDifot(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Active duration intervals per load overlapping the window. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.LoadActiveDuration(
          l.assignedTruck.id,
          COALESCE(l.dispatchedAt, l.createdAt),
          COALESCE(l.deliveredAt, :to))
      FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.assignedTruck IS NOT NULL
        AND COALESCE(l.dispatchedAt, l.createdAt) < :to
        AND COALESCE(l.deliveredAt, :to) >= :from
      """)
  List<LoadActiveDuration> findActiveLoadDurations(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Sum of loaded distance for delivered loads in the window. */
  @Query(
      """
      SELECT SUM(l.distance) FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.deliveredAt >= :from AND l.deliveredAt < :to
      """)
  Double sumLoadedMiles(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Distinct trucks that carried a load in the window. */
  @Query(
      """
      SELECT DISTINCT l.assignedTruck.id FROM Load l
      WHERE LOWER(l.status) IN :statuses
        AND l.assignedTruck IS NOT NULL
        AND COALESCE(l.dispatchedAt, l.deliveredAt) >= :from
        AND COALESCE(l.dispatchedAt, l.deliveredAt) < :to
      """)
  List<UUID> findTruckIdsWithActivity(
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Loads in the window, for the completeness block. */
  @Query(
      """
      SELECT COUNT(l) FROM Load l
      WHERE l.deliveredAt >= :from AND l.deliveredAt < :to
      """)
  long countDeliveredInWindow(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** The stored status distribution, unnormalised, so unrecognised values stay visible. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(l.status, COUNT(l))
      FROM Load l
      GROUP BY l.status
      ORDER BY COUNT(l) DESC
      """)
  List<GroupCount> groupByStatus();
}
