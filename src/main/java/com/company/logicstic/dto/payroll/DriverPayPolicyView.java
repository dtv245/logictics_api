package com.company.logicstic.dto.payroll;

import com.company.logicstic.entity.DriverPayPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DriverPayPolicyView(UUID id, String policyCode, String name, UUID driverId, String payMethod,
        BigDecimal perMileRate, BigDecimal perLoadRate, BigDecimal hourlyRate, BigDecimal dailyRate,
        BigDecimal flatRate, BigDecimal revenuePercentage, String mileageBasis, String revenueBasis,
        BigDecimal detentionRate, Integer detentionFreeMinutes, Integer detentionBlockMinutes,
        BigDecimal layoverRate, BigDecimal stopPayRate, String currency, LocalDate effectiveFrom,
        LocalDate effectiveTo, Integer policyVersion, Boolean active) {
    public static DriverPayPolicyView from(DriverPayPolicy p) {
        return new DriverPayPolicyView(p.getId(), p.getPolicyCode(), p.getName(), p.getDriver() == null ? null : p.getDriver().getId(),
                p.getPayMethod(), p.getPerMileRate(), p.getPerLoadRate(), p.getHourlyRate(), p.getDailyRate(), p.getFlatRate(),
                p.getRevenuePercentage(), p.getMileageBasis(), p.getRevenueBasis(), p.getDetentionRate(),
                p.getDetentionFreeMinutes(), p.getDetentionBlockMinutes(), p.getLayoverRate(), p.getStopPayRate(),
                p.getCurrency(), p.getEffectiveFrom(), p.getEffectiveTo(), p.getPolicyVersion(), p.getActive());
    }
}
