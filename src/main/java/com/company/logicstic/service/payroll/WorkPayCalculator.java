package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.entity.TimeEntry;
import com.company.logicstic.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

/** Explicit quantities supplied by eligible load/time/period sources; no legacy invoice payroll inputs. */
@Component @RequiredArgsConstructor
public class WorkPayCalculator {
    private final FinancialRoundingPolicy rounding;
    public record Result(UUID policyId, Integer policyVersion, String method, String sourceType, UUID sourceId,
            BigDecimal eligibleQuantity, String unit, BigDecimal rate, BigDecimal rawAmount, BigDecimal amount,
            String currency, String roundingPolicyVersion) {}
    public Result perLoad(UUID loadId, DriverPayPolicy policy) {
        if (loadId == null) throw new BadRequestException("LOAD_PAY_VALIDATION_REQUIRED", "Explicit eligible load required");
        return calculate(policy,"PER_LOAD","LOAD",loadId,BigDecimal.ONE,"LOAD",policy.getPerLoadRate());
    }
    public Result hourly(TimeEntry entry, DriverPayPolicy policy) {
        if (entry.getId() == null || entry.getTotalHours() == null) throw new BadRequestException("HOURS_VALIDATION_REQUIRED", "Persisted time entry and total_hours required");
        return calculate(policy,"HOURLY","TIME_ENTRY",entry.getId(),entry.getTotalHours(),"HOUR",policy.getHourlyRate());
    }
    public Result daily(UUID entryId, DriverPayPolicy policy) {
        return calculate(policy,"DAILY","TIME_ENTRY",entryId,BigDecimal.ONE,"DAY",policy.getDailyRate());
    }
    public Result flat(UUID periodId, DriverPayPolicy policy) {
        return calculate(policy,"FLAT_RATE","PAY_PERIOD",periodId,BigDecimal.ONE,"PERIOD",policy.getFlatRate());
    }
    private Result calculate(DriverPayPolicy policy, String method, String sourceType, UUID sourceId,
            BigDecimal quantity, String unit, BigDecimal rate) {
        if (!method.equals(policy.getPayMethod()) || sourceId == null || quantity == null || quantity.signum() < 0 || rate == null || rate.signum() < 0)
            throw new BadRequestException("WORK_PAY_VALIDATION_REQUIRED", "Explicit valid source, matching method, quantity and rate required");
        String currency = CurrencyGuard.canonical(policy.getCurrency()); BigDecimal raw = quantity.multiply(rate);
        return new Result(policy.getId(),policy.getPolicyVersion(),method,sourceType,sourceId,quantity,unit,rate,raw,
                rounding.money(raw,currency,FinancialRoundingPolicy.Boundary.ALLOCATION),currency,rounding.version());
    }
}
