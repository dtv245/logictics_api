package com.company.logicstic.service.calculation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.company.logicstic.dto.report.DeliveryDelayReport;
import com.company.logicstic.dto.report.ExceptionSummaryReport;
import com.company.logicstic.dto.report.OtdReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Backward-compatible facade for the dedicated Phase 1 operation calculators. */
@Service
public class OperationsMetricsService {
    private final OnTimeCalculator onTimeCalculator;
    private final DelayCalculator delayCalculator;
    private final ExceptionMetricsService exceptionMetricsService;
    private final ZoneId appZoneId;

    @Autowired
    public OperationsMetricsService(OnTimeCalculator onTimeCalculator, DelayCalculator delayCalculator,
                                   ExceptionMetricsService exceptionMetricsService, ZoneId appZoneId) {
        this.onTimeCalculator = onTimeCalculator;
        this.delayCalculator = delayCalculator;
        this.exceptionMetricsService = exceptionMetricsService;
        this.appZoneId = appZoneId != null ? appZoneId : ZoneOffset.UTC;
    }

    public OperationsMetricsService(OnTimeCalculator onTimeCalculator, DelayCalculator delayCalculator,
                                   ExceptionMetricsService exceptionMetricsService) {
        this(onTimeCalculator, delayCalculator, exceptionMetricsService, ZoneOffset.UTC);
    }

    public OtdReport calculateOtd(LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay(appZoneId).toOffsetDateTime() : null;
        OffsetDateTime endExclusive = to != null ? to.plusDays(1).atStartOfDay(appZoneId).toOffsetDateTime() : null;
        return onTimeCalculator.calculate(start, endExclusive);
    }

    private OtdReport calculateOtd(OffsetDateTime from, OffsetDateTime to) {
        return onTimeCalculator.calculate(from, to);
    }

    public DeliveryDelayReport calculateDelays(LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay(appZoneId).toOffsetDateTime() : null;
        OffsetDateTime endExclusive = to != null ? to.plusDays(1).atStartOfDay(appZoneId).toOffsetDateTime() : null;
        return delayCalculator.calculate(start, endExclusive);
    }

    private DeliveryDelayReport calculateDelays(OffsetDateTime from, OffsetDateTime to) {
        return delayCalculator.calculate(from, to);
    }

    public ExceptionSummaryReport calculateExceptions(LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay(appZoneId).toOffsetDateTime() : null;
        OffsetDateTime endExclusive = to != null ? to.plusDays(1).atStartOfDay(appZoneId).toOffsetDateTime() : null;
        return exceptionMetricsService.calculate(start, endExclusive);
    }

    private ExceptionSummaryReport calculateExceptions(OffsetDateTime from, OffsetDateTime to) {
        return exceptionMetricsService.calculate(from, to);
    }
}
