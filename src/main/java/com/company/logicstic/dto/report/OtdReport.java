package com.company.logicstic.dto.report;

import java.math.BigDecimal;

public record OtdReport(
        int totalEligibleLoads,
        int onTimeLoads,
        BigDecimal otdPercentage,
        BigDecimal averageDelayMinutes
) {}
