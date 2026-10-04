package com.company.logicstic.service.accessorial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import com.company.logicstic.dto.accessorial.DetentionCalculationRequest;
import com.company.logicstic.dto.accessorial.DetentionCalculationResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetentionCalculator {

    private final FinancialRoundingPolicy rounding;

    public DetentionCalculationResult calculate(
            UUID stopId,
            OffsetDateTime arrivedAt,
            OffsetDateTime departedAt,
            DetentionCalculationRequest req
    ) {
        String currency = CurrencyGuard.canonical(req.currency());
        if (arrivedAt == null || departedAt == null || departedAt.isBefore(arrivedAt)) {
            throw new BadRequestException("DETENTION_TIMESTAMPS_INVALID", "Arrival and departure in chronological order are required");
        }
        if (req.freeMinutes() < 0 || req.blockMinutes() < 0 || req.hourlyRate() == null || req.hourlyRate().signum() < 0
                || (req.driverHourlyRate() != null && req.driverHourlyRate().signum() < 0)) {
            throw new BadRequestException("DETENTION_POLICY_INVALID", "Non-negative minutes and rates are required");
        }
        Duration dwell = Duration.between(arrivedAt, departedAt);
        BigDecimal seconds = BigDecimal.valueOf(dwell.getSeconds()).add(BigDecimal.valueOf(dwell.getNano(), 9));
        BigDecimal excessSeconds = seconds.subtract(BigDecimal.valueOf(req.freeMinutes()).multiply(BigDecimal.valueOf(60))).max(BigDecimal.ZERO);
        BigDecimal chargedSeconds = excessSeconds;
        BigDecimal units;
        if (req.blockMinutes() > 0) {
            BigDecimal blockSeconds = BigDecimal.valueOf(req.blockMinutes()).multiply(BigDecimal.valueOf(60));
            units = excessSeconds.divide(blockSeconds, 0, RoundingMode.CEILING);
            chargedSeconds = units.multiply(blockSeconds);
        } else {
            units = rounding.divide(excessSeconds, BigDecimal.valueOf(3600), 6, FinancialRoundingPolicy.Boundary.REPORT);
        }
        // Multiply before division; never price using rounded display units/fractions.
        int scale = com.company.logicstic.common.MoneyRoundingPolicy.getScaleForCurrency(currency);
        BigDecimal customerAmount = rounding.divide(chargedSeconds.multiply(req.hourlyRate()), BigDecimal.valueOf(3600), scale,
                FinancialRoundingPolicy.Boundary.INVOICE);
        BigDecimal driverRate = req.driverHourlyRate() == null ? BigDecimal.ZERO : req.driverHourlyRate();
        BigDecimal driverAmount = rounding.divide(chargedSeconds.multiply(driverRate), BigDecimal.valueOf(3600), scale,
                FinancialRoundingPolicy.Boundary.REPORT);
        return new DetentionCalculationResult(
                stopId, seconds.divideToIntegralValue(BigDecimal.valueOf(60)).longValueExact(),
                excessSeconds.divideToIntegralValue(BigDecimal.valueOf(60)).longValueExact(), units,
                customerAmount, driverAmount, currency, seconds, excessSeconds
        );
    }
}
