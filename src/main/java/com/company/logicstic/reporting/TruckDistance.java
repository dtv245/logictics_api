package com.company.logicstic.reporting;

import java.util.UUID;

/**
 * Distance one truck covered, from the spread of its odometer readings in the window.
 *
 * <p>Readings are cumulative, so distance is the difference between the highest and lowest reading
 * in the window — never their sum, which would add unrelated totals together.
 *
 * <p>{@code readingCount} travels with the row because a truck with a single reading has a spread
 * of exactly zero, and that zero means "we have one data point", not "this truck did not move". The
 * service excludes such trucks from total distance and reports how many it excluded, so an unfed
 * odometer reads as missing data rather than as a stationary fleet.
 *
 * <p>Two caveats that no column can fix, stated here so a reader of the number knows them. The
 * window's first reading is a starting point, not a start-of-window boundary, so distance driven
 * between {@code from} and the first reading is not counted — total distance is a slight
 * under-estimate whenever a window does not begin exactly on a reading. And readings recorded
 * against the wrong truck are indistinguishable from correct ones.
 *
 * @param truckId the truck the readings belong to
 * @param lowestReading the smallest odometer value in the window
 * @param highestReading the largest odometer value in the window
 * @param readingCount how many readings the window contained for this truck
 */
public record TruckDistance(
    UUID truckId, Integer lowestReading, Integer highestReading, long readingCount) {}
