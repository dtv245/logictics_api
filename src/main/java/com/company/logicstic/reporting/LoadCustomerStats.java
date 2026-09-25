package com.company.logicstic.reporting;

import java.util.UUID;

/**
 * One customer's delivered-load record: distance, volume, and on-time performance.
 *
 * <p>{@code comparableCount} is separate from {@code loadCount} on purpose. On-time performance is
 * only meaningful for a load that has both a delivery timestamp and a requested delivery date; a
 * load missing either cannot be late or on time, and counting it in the denominator would quietly
 * dilute the percentage. The numerator and denominator both use {@code comparableCount}, so a
 * customer whose loads carry no requested dates reports "unknown" rather than a flattering 100%.
 *
 * @param customerId customer identifier
 * @param miles total loaded distance for this customer, or {@code null} when none was recorded
 * @param loadCount delivered loads, whether or not they can be judged for punctuality
 * @param comparableCount delivered loads carrying both a delivery timestamp and a requested date
 * @param onTimeCount of those, how many were delivered no later than requested
 */
public record LoadCustomerStats(
    UUID customerId, Double miles, long loadCount, long comparableCount, long onTimeCount) {}
