package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import com.company.logicstic.dto.report.TransitTimeReport;
import com.company.logicstic.entity.Load;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransitTimeCalculator {

    private final LoadRepository loadRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public TransitTimeReport calculate(OffsetDateTime from, OffsetDateTime to) {
        List<Load> loads = loadRepository.findAll();
        long eligible = 0;
        long totalMillis = 0;
        for (Load load : loads) {
            OffsetDateTime pickedUpAt = load.getPickedUpAt();
            OffsetDateTime deliveredAt = load.getDeliveredAt();
            if (pickedUpAt == null || deliveredAt == null || deliveredAt.isBefore(pickedUpAt)) {
                continue;
            }
            if ((from != null && deliveredAt.isBefore(from)) || (to != null && deliveredAt.isAfter(to))) {
                continue;
            }
            eligible++;
            totalMillis += Duration.between(pickedUpAt, deliveredAt).toMillis();
        }
        BigDecimal averageMinutes = eligible == 0
                ? null
                : rounding.divide(BigDecimal.valueOf(totalMillis), BigDecimal.valueOf(eligible * 60000L), 2, REPORT);
        return new TransitTimeReport(eligible, averageMinutes);
    }
}
