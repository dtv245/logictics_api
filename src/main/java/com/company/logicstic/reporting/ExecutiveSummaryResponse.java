package com.company.logicstic.reporting;

/**
 * Headline counts for the executive screen's North Star row.
 *
 * <p>The unavailable members are part of the contract on purpose. The client maps this payload onto
 * a fixed set of metric keys, so a key that is absent and a key that is explicitly unavailable are
 * different things: the first makes the frontend fall back to its "no endpoint yet" frame, the
 * second lets it show the real reason. {@code fleetUtilizationPct}, {@code loadedMilesPct} and
 * {@code difotPct} are therefore present and permanently unavailable until the schema can support
 * them — see {@link #trucksWithLoadsPct} for the honest substitute that is offered instead.
 *
 * @param fleetSize registered vehicles. No status filter: the fleet is what is registered, and
 *     every status value this schema actually contains is unverified free text
 * @param activeCustomers customers whose status reads as active
 * @param fleetUtilizationPct never available. Utilisation needs available-vehicle-time, and this
 *     schema records neither an odometer nor any status history, so there is no window to measure
 *     against. A migration cannot create history that was never written
 * @param loadedMilesPct never available. The numerator exists ({@code loads.distance}) but the
 *     denominator does not: nothing records empty or deadhead miles. {@code trips.total_distance}
 *     is set equal to the load distance by the seeder, so any ratio built on it would read 100% by
 *     construction — a fabricated number, not a measurement
 * @param onTimeDeliveryPct delivered loads that met their requested delivery date
 * @param difotPct never available. DIFOT needs ordered versus delivered quantities, and this schema
 *     stores neither. {@code condition_defects} records damage, not shortfall
 * @param trucksWithLoadsPct the share of the fleet that carried at least one dispatched load in the
 *     period. This is <em>activity</em>, not <em>utilisation</em>, which is why it is reported
 *     under its own key rather than substituted into {@code fleetUtilizationPct}
 * @param completeness what the aggregate read
 */
public record ExecutiveSummaryResponse(
    MetricValue fleetSize,
    MetricValue activeCustomers,
    MetricValue fleetUtilizationPct,
    MetricValue loadedMilesPct,
    MetricValue onTimeDeliveryPct,
    MetricValue difotPct,
    MetricValue trucksWithLoadsPct,
    DataCompleteness completeness) {}
