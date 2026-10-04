package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

import com.company.logicstic.dto.report.OtdReport;
import com.company.logicstic.entity.Load;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnTimeCalculator {
    private final LoadRepository loadRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public OtdReport calculate(OffsetDateTime from, OffsetDateTime to) {
        List<Load> loads = loadRepository.findAll();
        int eligible = 0;
        int onTime = 0;
        long lateMillis = 0;
        for (Load load : loads) {
            OffsetDateTime delivered = load.getDeliveredAt();
            OffsetDateTime deadline = load.getRequestedDeliveryDate();
            if (delivered == null || deadline == null || !within(delivered, from, to)) continue;
            eligible++;
            if (!delivered.isAfter(deadline)) onTime++;
            else lateMillis += java.time.Duration.between(deadline, delivered).toMillis();
        }
        if (eligible == 0) return new OtdReport(0, 0, null, null);
        BigDecimal pct = rounding.divide(BigDecimal.valueOf(onTime * 100L), BigDecimal.valueOf(eligible), 2, REPORT);
        BigDecimal averageDelay = rounding.divide(BigDecimal.valueOf(lateMillis), BigDecimal.valueOf(eligible * 60000L), 2, REPORT);
        return new OtdReport(eligible, onTime, pct, averageDelay);
    }

    static boolean within(OffsetDateTime value, OffsetDateTime from, OffsetDateTime to) {
        return (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }
}
