package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.company.logicstic.dto.report.ExceptionSummaryReport;
import com.company.logicstic.entity.LoadException;
import com.company.logicstic.repository.LoadExceptionRepository;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExceptionMetricsService {
    private final LoadExceptionRepository loadExceptionRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public ExceptionSummaryReport calculate(OffsetDateTime from, OffsetDateTime to) {
        List<LoadException> exceptions = loadExceptionRepository.findByPeriod(from, to);
        long resolved = 0;
        long unresolved = 0;
        long measuredResolutionCount = 0;
        long resolutionMillis = 0;
        Map<String, Long> byType = new HashMap<>();
        for (LoadException exception : exceptions) {
            String type = exception.getType() == null ? "UNKNOWN" : exception.getType();
            byType.merge(type, 1L, Long::sum);
            if (exception.getResolvedAt() == null) unresolved++;
            else {
                resolved++;
                if (exception.getOccurredAt() != null && !exception.getResolvedAt().isBefore(exception.getOccurredAt())) {
                    measuredResolutionCount++;
                    resolutionMillis += Duration.between(exception.getOccurredAt(), exception.getResolvedAt()).toMillis();
                }
            }
        }
        BigDecimal average = measuredResolutionCount == 0 ? null
                : rounding.divide(BigDecimal.valueOf(resolutionMillis), BigDecimal.valueOf(measuredResolutionCount * 60000L), 2, REPORT);
        return new ExceptionSummaryReport(exceptions.size(), resolved, unresolved, average, byType);
    }
}
