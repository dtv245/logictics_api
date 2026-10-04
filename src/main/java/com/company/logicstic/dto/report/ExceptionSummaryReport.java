package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import java.util.Map;

public record ExceptionSummaryReport(
        long totalExceptions,
        long resolvedExceptions,
        long unresolvedExceptions,
        BigDecimal averageResolutionMinutes,
        Map<String, Long> countByType
) {}
