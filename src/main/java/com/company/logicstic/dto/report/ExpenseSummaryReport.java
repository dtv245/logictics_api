package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import java.util.Map;

public record ExpenseSummaryReport(
        BigDecimal totalApprovedAmount,
        String currency,
        Map<String, BigDecimal> byCategory,
        int count
) {}
