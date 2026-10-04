package com.company.logicstic.dto.trip;

import com.company.logicstic.common.MetricDto;

public record TripMileageRatioReport(
        MetricDto loadedMilePercent,
        MetricDto emptyMilePercent
) {
}
