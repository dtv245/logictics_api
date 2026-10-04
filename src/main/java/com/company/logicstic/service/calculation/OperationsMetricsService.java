package com.company.logicstic.service.calculation;

import java.time.OffsetDateTime;

import com.company.logicstic.dto.report.DeliveryDelayReport;
import com.company.logicstic.dto.report.ExceptionSummaryReport;
import com.company.logicstic.dto.report.OtdReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Backward-compatible facade for the dedicated Phase 1 operation calculators. */
@Service
@RequiredArgsConstructor
public class OperationsMetricsService {
    private final OnTimeCalculator onTimeCalculator;
    private final DelayCalculator delayCalculator;
    private final ExceptionMetricsService exceptionMetricsService;

    public OtdReport calculateOtd(OffsetDateTime from, OffsetDateTime to) {
        return onTimeCalculator.calculate(from, to);
    }

    public DeliveryDelayReport calculateDelays(OffsetDateTime from, OffsetDateTime to) {
        return delayCalculator.calculate(from, to);
    }

    public ExceptionSummaryReport calculateExceptions(OffsetDateTime from, OffsetDateTime to) {
        return exceptionMetricsService.calculate(from, to);
    }
}
