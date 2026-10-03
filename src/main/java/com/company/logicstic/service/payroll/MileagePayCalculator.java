package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.entity.TripDriverAssignment;
import com.company.logicstic.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class MileagePayCalculator {
    private final FinancialRoundingPolicy rounding;
    public record Result(UUID assignmentId, UUID policyId, Integer policyVersion, String mileageBasis,
            BigDecimal eligibleMiles, BigDecimal rate, BigDecimal rawAmount, BigDecimal amount, String currency,
            String roundingPolicyVersion) {}

    public Result calculate(TripDriverAssignment assignment, DriverPayPolicy policy) {
        String basis = policy.getMileageBasis() == null ? "" : policy.getMileageBasis().trim().toUpperCase(Locale.ROOT);
        BigDecimal miles;
        switch (basis) {
            case "ACTUAL", "ACTUAL_MILES", "ACTUAL_ALL_MILES" -> { basis = "ACTUAL_ALL_MILES"; miles = assignment.getActualMiles(); }
            case "PLANNED", "PLANNED_MILES", "PLANNED_ALL_MILES" -> { basis = "PLANNED_ALL_MILES"; miles = assignment.getPlannedMiles(); }
            default -> throw new BadRequestException("MILEAGE_BASIS_UNAVAILABLE", "Selected driver mileage basis has no supported source");
        }
        if (miles == null || miles.signum() < 0) throw new BadRequestException("MILEAGE_VALIDATION_REQUIRED", "Selected assignment mileage is missing or invalid");
        BigDecimal rate = policy.getPerMileRate();
        if (rate == null || rate.signum() < 0) throw new BadRequestException("MILEAGE_RATE_REQUIRED", "Explicit non-negative per-mile rate required");
        String currency = CurrencyGuard.canonical(policy.getCurrency());
        BigDecimal raw = miles.multiply(rate);
        return new Result(assignment.getId(),policy.getId(),policy.getPolicyVersion(),basis,miles,rate,raw,
                rounding.money(raw,currency,FinancialRoundingPolicy.Boundary.ALLOCATION),currency,rounding.version());
    }
}
