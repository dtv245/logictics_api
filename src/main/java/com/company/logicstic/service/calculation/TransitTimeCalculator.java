package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.company.logicstic.dto.report.TransitTimeReport;
import com.company.logicstic.entity.Load;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransitTimeCalculator {

    private final LoadRepository loadRepository;
    private final FinancialRoundingPolicy rounding;
    private final ZoneId appZoneId;

    @Autowired
    public TransitTimeCalculator(LoadRepository loadRepository, FinancialRoundingPolicy rounding, ZoneId appZoneId) {
        this.loadRepository = loadRepository;
        this.rounding = rounding;
        this.appZoneId = appZoneId != null ? appZoneId : ZoneOffset.UTC;
    }

    public TransitTimeCalculator(LoadRepository loadRepository, FinancialRoundingPolicy rounding) {
        this(loadRepository, rounding, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public TransitTimeReport calculate(LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay(appZoneId).toOffsetDateTime() : null;
        OffsetDateTime endExclusive = to != null ? to.plusDays(1).atStartOfDay(appZoneId).toOffsetDateTime() : null;
        return calculate(start, endExclusive);
    }

    private TransitTimeReport calculate(OffsetDateTime from, OffsetDateTime to) {
        List<Load> loads = loadRepository.findAll();
        long eligible = 0;
        long totalMillis = 0;
        for (Load load : loads) {
            OffsetDateTime pickedUpAt = load.getPickedUpAt();
            OffsetDateTime deliveredAt = load.getDeliveredAt();
            if (pickedUpAt == null || deliveredAt == null || deliveredAt.isBefore(pickedUpAt)) {
                continue;
            }
            if ((from != null && deliveredAt.isBefore(from)) || (to != null && !deliveredAt.isBefore(to))) {
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
