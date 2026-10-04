package com.company.logicstic.service.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.entity.Trip;
import org.junit.jupiter.api.Test;

class TripMileageRatioCalculatorTest {

    private final TripMileageRatioCalculator calculator = new TripMileageRatioCalculator();

    @Test
    void calculatesRatiosFromExplicitV3MileageOnly() {
        Trip trip = new Trip();
        trip.setTotalDistance(9999d); // Must not affect the result.
        trip.setActualDistanceMiles(new BigDecimal("400.000"));
        trip.setLoadedMiles(new BigDecimal("300.000"));
        trip.setEmptyMiles(new BigDecimal("100.000"));

        var report = calculator.calculate(trip);

        assertEquals(new BigDecimal("75.00"), report.loadedMilePercent().value());
        assertEquals(new BigDecimal("25.00"), report.emptyMilePercent().value());
        assertEquals(MetricAvailability.AVAILABLE, report.loadedMilePercent().availability());
    }

    @Test
    void reportsUnavailableWhenActualMilesAreMissing() {
        Trip trip = new Trip();
        trip.setTotalDistance(400d);
        trip.setLoadedMiles(new BigDecimal("300.000"));

        var report = calculator.calculate(trip);

        assertNull(report.loadedMilePercent().value());
        assertEquals(MetricAvailability.UNAVAILABLE, report.loadedMilePercent().availability());
    }
}
