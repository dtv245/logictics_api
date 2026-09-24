package com.company.logicstic.reporting;

import com.company.logicstic.fleet.maintenance.MaintenanceSchedule;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads over preventive-maintenance schedules, the one maintenance source that can be judged today.
 *
 * <p>A schedule carries a real next-due date, so "is this overdue" is answerable. Everything else
 * about fleet health needs records this schema does not hold — see {@code FleetHealthResponse}.
 *
 * <p>Counts schedules, not vehicles. A truck with three overdue schedules counts as three misses,
 * which is how preventive maintenance is actually worked: each schedule is a separate job with its
 * own due date, and collapsing them to one miss per truck would hide two of the three.
 *
 * <p>Schedules with no next-due date at all are excluded from both sides of the ratio rather than
 * counted as compliant. A schedule that has never been given a due date has not been shown to be up
 * to date, and treating its silence as compliance is the same error as reading a null metric as
 * zero.
 */
public interface ReportingMaintenanceScheduleRepository
    extends JpaRepository<MaintenanceSchedule, UUID> {

  /** Active schedules that carry a next-due date and so can be judged. */
  @Query(
      """
      SELECT COUNT(s) FROM MaintenanceSchedule s
      WHERE s.isActive = TRUE
        AND s.nextDueDate IS NOT NULL
      """)
  long countActiveWithDueDate();

  /** Of those, how many are not yet past due as of the given instant. */
  @Query(
      """
      SELECT COUNT(s) FROM MaintenanceSchedule s
      WHERE s.isActive = TRUE
        AND s.nextDueDate IS NOT NULL
        AND s.nextDueDate >= :asOf
      """)
  long countActiveWithDueDateNotPast(@Param("asOf") OffsetDateTime asOf);

  /**
   * Active schedules whose interval is defined by mileage but which carry no next-due mileage.
   *
   * <p>Vocabulary-free on purpose: it does not attempt to read {@code interval_type}, whose values
   * are unenumerated free text, and instead detects the situation that actually matters — a mileage
   * interval was configured, so this schedule is meant to be judged on distance, but no distance
   * target was ever set. Those schedules are left out of the compliance ratio and their count is
   * reported, so the exclusion is visible instead of quietly improving the figure.
   */
  @Query(
      """
      SELECT COUNT(s) FROM MaintenanceSchedule s
      WHERE s.isActive = TRUE
        AND s.mileageInterval IS NOT NULL
        AND s.nextDueMileage IS NULL
      """)
  long countActiveMileageBasedWithoutTarget();

  /** The interval-type distribution, unnormalised — the column has no enumerated vocabulary. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(s.intervalType, COUNT(s))
      FROM MaintenanceSchedule s
      GROUP BY s.intervalType
      ORDER BY COUNT(s) DESC
      """)
  List<GroupCount> groupByIntervalType();
}
