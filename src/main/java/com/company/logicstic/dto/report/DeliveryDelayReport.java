package com.company.logicstic.dto.report;

import java.math.BigDecimal;

public record DeliveryDelayReport(
        int eligibleLoads,
        int totalLateLoads,
        BigDecimal lateDeliveryPercentage,
        BigDecimal averageDelayMinutes,
        BigDecimal maxDelayMinutes
) {}
