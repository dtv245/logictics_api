package com.company.logicstic.reporting;

/**
 * One month of delivered-load distance.
 *
 * <p><strong>This is loaded distance, not total distance.</strong> {@code loads.distance} is the
 * distance of a load that was carried; nothing in this schema records the miles a truck drove empty
 * between loads. The two are reported under different names for that reason — {@code loadedMiles}
 * here, {@code totalMiles} separately from odometer readings — so a client can never mistake one
 * for the other by reading a shared key.
 *
 * <p>Buckets on {@code delivered_at}, because the miles were driven when the load was delivered,
 * not when the row was created.
 *
 * @param year the UTC calendar year
 * @param month the UTC calendar month, 1-based
 * @param miles total loaded distance, or {@code null} when the driving rows carry no distance
 * @param loadCount how many delivered loads contributed
 */
public record LoadMonthlyMiles(int year, int month, Double miles, long loadCount) {}
