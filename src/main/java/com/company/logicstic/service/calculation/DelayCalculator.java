package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import com.company.logicstic.dto.report.DeliveryDelayReport;
import com.company.logicstic.entity.Load;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DelayCalculator {
    private final LoadRepository loadRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public DeliveryDelayReport calculate(OffsetDateTime from, OffsetDateTime to) {
        List<Load> loads = loadRepository.findAll();
        int eligible = 0;
        int lateCount = 0;
        long totalMillis = 0;
        long maxMillis = 0;
        for (Load load : loads) {
            OffsetDateTime delivered = load.getDeliveredAt();
            OffsetDateTime deadline = load.getRequestedDeliveryDate();
            if (delivered == null || deadline == null || !OnTimeCalculator.within(delivered, from, to)) continue;
            eligible++;
            if (!delivered.isAfter(deadline)) continue;
            long millis = Duration.between(deadline, delivered).toMillis();
            lateCount++;
            totalMillis += millis;
            maxMillis = Math.max(maxMillis, millis);
        }
        BigDecimal average = eligible == 0 ? null : lateCount == 0 ? BigDecimal.ZERO
                : rounding.divide(BigDecimal.valueOf(totalMillis), BigDecimal.valueOf(lateCount * 60000L), 2, REPORT);
        BigDecimal latePercentage = eligible == 0 ? null
                : rounding.divide(BigDecimal.valueOf(lateCount * 100L), BigDecimal.valueOf(eligible), 2, REPORT);
        BigDecimal max = eligible == 0 ? null : rounding.divide(BigDecimal.valueOf(maxMillis), BigDecimal.valueOf(60000), 2, REPORT);
        return new DeliveryDelayReport(eligible, lateCount, latePercentage, average, max);
    }
}
