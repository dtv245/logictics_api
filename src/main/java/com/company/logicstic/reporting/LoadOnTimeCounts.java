package com.company.logicstic.reporting;

/**
 * Fleet-wide on-time delivery, counted rather than averaged.
 *
 * <p>Counts rather than a pre-divided percentage, so the service can compute the ratio itself and
 * handle the zero case explicitly. A query that returned {@code onTime / total} would divide by
 * zero on an empty window, and whatever the database chose to do about that — an error, or a null
 * that a client reads as zero — would be worse than the caller knowing the denominator was zero.
 *
 * @param comparableCount delivered loads carrying both a delivery timestamp and a requested date
 * @param onTimeCount of those, how many were delivered no later than requested
 */
public record LoadOnTimeCounts(long comparableCount, long onTimeCount) {}
