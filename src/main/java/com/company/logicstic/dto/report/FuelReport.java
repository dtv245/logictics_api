package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import com.company.logicstic.common.MetricDto;

public record FuelReport(
        BigDecimal totalFuelCost,
        BigDecimal totalGallons,
        BigDecimal averageCostPerGallon,
        String currency,
        MetricDto mpg
) {}
