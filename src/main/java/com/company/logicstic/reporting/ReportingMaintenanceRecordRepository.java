package com.company.logicstic.reporting;

import com.company.logicstic.fleet.maintenance.MaintenanceRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads over maintenance records for reporting.
 *
 * <p>{@code total_cost_currency} is a column added by {@code V4__add_reporting_inputs} and is null
 * on every row that existed before it, so every sum here is filtered on it. A maintenance cost
 * total summed without a currency predicate would add amounts whose units were never recorded — the
 * one monetary column in this schema that never had a currency beside it. Rows it excludes are
 * counted by {@link #countWithoutCurrency} and reported, so an empty maintenance total says which
 * of the two reasons it is.
 *
 * <p>Nothing here computes downtime. {@code service_date} marks when work happened; the columns
 * that would make downtime a span are new and therefore null on every historical row, and the
 * denominator — how long each vehicle was available — has no source at all.
 */
public interface ReportingMaintenanceRecordRepository
    extends JpaRepository<MaintenanceRecord, UUID> {

  /** Total maintenance cost in the window, among rows that record their currency. */
  @Query(
      """
      SELECT SUM(m.totalCost) FROM MaintenanceRecord m
      WHERE m.totalCostCurrency = :currency
        AND m.serviceDate >= :from AND m.serviceDate < :to
      """)
  BigDecimal sumTotalCost(
      @Param("currency") String currency,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Records in the window whose currency was never recorded. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.totalCostCurrency IS NULL
        AND m.serviceDate >= :from AND m.serviceDate < :to
      """)
  long countWithoutCurrency(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Records in the window that record a currency other than the requested one. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.totalCostCurrency IS NOT NULL
        AND m.totalCostCurrency <> :currency
        AND m.serviceDate >= :from AND m.serviceDate < :to
      """)
  long countInOtherCurrency(
      @Param("currency") String currency,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Total records in the window, whatever their currency column says. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.serviceDate >= :from AND m.serviceDate < :to
      """)
  long countInWindow(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Records in the window that carry no planned/unplanned classification. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.isUnplanned IS NULL
        AND m.serviceDate >= :from AND m.serviceDate < :to
      """)
  long countUnclassified(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Records in the window explicitly classified as unplanned. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.isUnplanned = TRUE
        AND m.serviceDate >= :from AND m.serviceDate < :to
      """)
  long countUnplanned(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Unplanned downtime intervals overlapping the window. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.DowntimeInterval(
          m.truck.id,
          m.downtimeStartAt,
          m.downtimeEndAt)
      FROM MaintenanceRecord m
      WHERE m.isUnplanned = TRUE
        AND m.downtimeStartAt IS NOT NULL
        AND m.downtimeStartAt < :to
        AND (m.downtimeEndAt IS NULL OR m.downtimeEndAt >= :from)
      """)
  List<DowntimeInterval> findUnplannedDowntimes(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Total records across all time that have a downtime start timestamp recorded. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.downtimeStartAt IS NOT NULL
      """)
  long countWithDowntimeInterval();

  /** Breakdown maintenance events in the window. */
  @Query(
      """
      SELECT COUNT(m) FROM MaintenanceRecord m
      WHERE m.isBreakdown = TRUE
        AND m.serviceDate >= :from AND m.serviceDate < :to
      """)
  long countBreakdowns(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** The maintenance-type distribution, unnormalised — the column has no enumerated vocabulary. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(m.maintenanceType, COUNT(m))
      FROM MaintenanceRecord m
      WHERE m.serviceDate >= :from AND m.serviceDate < :to
      GROUP BY m.maintenanceType
      ORDER BY COUNT(m) DESC
      """)
  List<GroupCount> groupByType(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
