package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.company.logicstic.common.MetricDto;
import com.company.logicstic.dto.trip.TripMileageRatioReport;
import com.company.logicstic.entity.Trip;
import org.springframework.stereotype.Service;

/** Calculates utilization only from the explicit V3 mileage fields, never legacy total_distance. */
@Service
public class TripMileageRatioCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String BASIS = "EXPLICIT_TRIP_MILEAGE_V3";

    public TripMileageRatioReport calculate(Trip trip) {
        BigDecimal actualMiles = trip.getActualDistanceMiles();
        if (actualMiles == null || actualMiles.signum() <= 0) {
            return new TripMileageRatioReport(
                    MetricDto.unavailable("LOADED_MILE_PERCENT", "PERCENT", "ACTUAL_DISTANCE_MILES_REQUIRED"),
                    MetricDto.unavailable("EMPTY_MILE_PERCENT", "PERCENT", "ACTUAL_DISTANCE_MILES_REQUIRED")
            );
        }

        return new TripMileageRatioReport(
                ratio("LOADED_MILE_PERCENT", trip.getLoadedMiles(), actualMiles),
                ratio("EMPTY_MILE_PERCENT", trip.getEmptyMiles(), actualMiles)
        );
    }

    private MetricDto ratio(String code, BigDecimal numerator, BigDecimal actualMiles) {
        if (numerator == null) {
            return MetricDto.partial(code, null, "PERCENT", null, actualMiles, BASIS, "MILEAGE_COMPONENT_NOT_RECORDED");
        }
        if (numerator.signum() < 0 || numerator.compareTo(actualMiles) > 0) {
            return MetricDto.unavailable(code, "PERCENT", "MILEAGE_COMPONENT_OUT_OF_RANGE");
        }
        BigDecimal percent = numerator.multiply(ONE_HUNDRED).divide(actualMiles, 2, RoundingMode.HALF_UP);
        return MetricDto.available(code, percent, "PERCENT", numerator, actualMiles, BASIS);
    }
}
