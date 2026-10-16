package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DriverRankingItem(
        String driverId,
        String driverName,
        String avatarUrl,
        int rank,
        BigDecimal score,
        int completedLoads,
        BigDecimal totalMiles,
        BigDecimal onTimePercent,
        BigDecimal revenue,
        BigDecimal driverPay,
        BigDecimal revenuePerMile,
        int exceptionsCount,
        @JsonProperty("isMock") boolean isMock
) {}
