package com.company.logicstic.dto.profitability;

import com.company.logicstic.common.MetricDto;

public record ProfitabilityMileageMetrics(
        MetricDto totalMiles,
        MetricDto loadedMiles,
        MetricDto emptyMiles,
        MetricDto revenuePerTotalMile,
        MetricDto costPerTotalMile,
        MetricDto breakEvenLoadedRate
) {}
