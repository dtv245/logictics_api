package com.company.logicstic.reporting;

import java.time.OffsetDateTime;

/**
 * Fleet maintenance health over a window.
 *
 * <p>Only one of the four metrics the client asks for is computable today, and the response says so
 * rather than substituting something adjacent. {@code pmCompliancePct} works because {@code
 * maintenance_schedules.next_due_date} is a real date to compare against; the other three need
 * records this schema has never held.
 *
 * @param currency the currency maintenance cost is denominated in
 * @param from inclusive start of the window
 * @param to exclusive end of the window
 * @param unplannedDowntimePct never available. Downtime is a span, and {@code
 *     maintenance_records.service_date} is an instant: it marks when work happened, not how long
 *     the vehicle was out. Even with {@code downtime_start_at} and {@code downtime_end_at} columns
 *     added, only records written after that migration could contribute, so the honest answer for
 *     any historical window remains "not known"
 * @param pmCompliancePct active schedules whose next due date has not passed. Counts schedules, not
 *     vehicles: one truck with three overdue schedules is three misses, which is the behaviour the
 *     maintenance team actually manages
 * @param maintenanceCostPerMile maintenance spend over distance driven. Available only when the
 *     rows in the window record a currency matching the request <em>and</em> odometer readings give
 *     a distance to divide by. {@code total_cost_currency} was added by the V4 migration and is
 *     null on every row that predates it, so a sum without that predicate would add amounts whose
 *     units were never recorded — the one monetary column in this schema that never had a currency
 *     beside it. Rows excluded for carrying no currency are counted in {@code completeness}
 * @param breakdownsPer100kMiles never available. "Breakdown" is not a distinguishable event in this
 *     schema — {@code maintenance_type} is free text with no enumerated vocabulary, and {@code
 *     is_breakdown} exists only for rows written after the V4 migration
 * @param schedulesRequiringOdometerCount active schedules whose interval is mileage-based but which
 *     carry no {@code next_due_mileage}. These cannot be judged on time and are excluded from both
 *     the numerator and the denominator of {@code pmCompliancePct}; this count is reported so the
 *     exclusion is visible instead of quietly improving the compliance figure
 * @param completeness what the aggregate read
 */
public record FleetHealthResponse(
    String currency,
    OffsetDateTime from,
    OffsetDateTime to,
    MetricValue unplannedDowntimePct,
    MetricValue pmCompliancePct,
    MetricValue maintenanceCostPerMile,
    MetricValue breakdownsPer100kMiles,
    long schedulesRequiringOdometerCount,
    DataCompleteness completeness) {}
