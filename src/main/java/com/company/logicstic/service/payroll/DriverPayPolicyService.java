package com.company.logicstic.service.payroll;

import com.company.logicstic.dto.payroll.DriverPayPolicyRequest;
import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.DriverPayPolicyRepository;
import com.company.logicstic.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DriverPayPolicyService {
    private final DriverPayPolicyRepository policies;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public List<DriverPayPolicy> list() { return policies.findAllByOrderByPolicyCodeAscPolicyVersionDesc(); }

    @Transactional(readOnly = true)
    public DriverPayPolicy get(UUID id) {
        return policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Driver pay policy not found"));
    }

    @Transactional
    public DriverPayPolicy create(DriverPayPolicyRequest request) {
        if (policies.findTopByPolicyCodeOrderByPolicyVersionDesc(request.policyCode()).isPresent())
            throw new BadRequestException("Policy code already exists; create a new version instead");
        return policies.save(build(request, 1));
    }

    @Transactional
    public DriverPayPolicy newVersion(UUID id, DriverPayPolicyRequest request) {
        if (request.effectiveFrom() == null) throw new BadRequestException("effectiveFrom is required");
        DriverPayPolicy previous = policies.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Driver pay policy not found"));
        DriverPayPolicy latest = policies.findTopByPolicyCodeOrderByPolicyVersionDesc(previous.getPolicyCode()).orElseThrow();
        if (!latest.getId().equals(previous.getId())) throw new BadRequestException("POLICY_VERSION_STALE", "Create a new version from the latest policy version");
        if (!previous.getPolicyCode().equals(request.policyCode())) throw new BadRequestException("Policy code cannot change between versions");
        UUID previousDriver = previous.getDriver() == null ? null : previous.getDriver().getId();
        if (!java.util.Objects.equals(previousDriver, request.driverId())) throw new BadRequestException("Policy driver scope cannot change between versions");
        if (!request.effectiveFrom().isAfter(previous.getEffectiveFrom())) throw new BadRequestException("New version must have a later effective date");
        return policies.save(build(request, previous.getPolicyVersion() + 1));
    }

    private DriverPayPolicy build(DriverPayPolicyRequest r, int version) {
        if (r.effectiveFrom() == null) throw new BadRequestException("effectiveFrom is required");
        if (r.policyCode() == null || r.policyCode().isBlank() || r.name() == null || r.name().isBlank()) throw new BadRequestException("Policy code and name are required");
        if (r.payMethod() == null) throw new BadRequestException("Pay method is required");
        if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())) throw new BadRequestException("effectiveTo must not precede effectiveFrom");
        DriverPayPolicy p = new DriverPayPolicy();
        p.setPolicyCode(r.policyCode()); p.setName(r.name()); p.setPayMethod(r.payMethod());
        p.setPerMileRate(r.perMileRate()); p.setPerLoadRate(r.perLoadRate()); p.setHourlyRate(r.hourlyRate());
        p.setDailyRate(r.dailyRate()); p.setFlatRate(r.flatRate()); p.setRevenuePercentage(r.revenuePercentage());
        p.setMileageBasis(mileageBasis(r.mileageBasis())); p.setRevenueBasis(r.revenueBasis()); p.setDetentionRate(r.detentionRate());
        p.setDetentionFreeMinutes(r.detentionFreeMinutes()); p.setDetentionBlockMinutes(r.detentionBlockMinutes());
        p.setLayoverRate(r.layoverRate()); p.setStopPayRate(r.stopPayRate()); p.setCurrency(com.company.logicstic.common.CurrencyGuard.canonical(r.currency()));
        p.setEffectiveFrom(r.effectiveFrom()); p.setEffectiveTo(r.effectiveTo()); p.setPolicyVersion(version); p.setActive(true);
        if (r.driverId() != null) p.setDriver(employees.findById(r.driverId()).orElseThrow(() -> new ResourceNotFoundException("Driver not found")));
        validateMethodInputs(p);
        return p;
    }

    private void validateMethodInputs(DriverPayPolicy p) {
        for (var rate : new java.math.BigDecimal[] {p.getPerMileRate(),p.getPerLoadRate(),p.getHourlyRate(),p.getDailyRate(),
                p.getFlatRate(),p.getRevenuePercentage(),p.getDetentionRate(),p.getLayoverRate(),p.getStopPayRate()}) {
            if (rate != null && rate.signum() < 0) throw new BadRequestException("Pay rates must be non-negative");
        }
        if (p.getRevenuePercentage() != null && p.getRevenuePercentage().compareTo(java.math.BigDecimal.ONE) > 0)
            throw new BadRequestException("Revenue percentage is a ratio between 0 and 1");
        for (var rate : new java.math.BigDecimal[] {p.getPerMileRate(),p.getHourlyRate(),p.getDetentionRate(),p.getRevenuePercentage()})
            requirePrecision(rate, 6);
        for (var rate : new java.math.BigDecimal[] {p.getPerLoadRate(),p.getDailyRate(),p.getFlatRate(),p.getLayoverRate(),p.getStopPayRate()})
            requirePrecision(rate, 4);
        if ((p.getDetentionFreeMinutes() != null && p.getDetentionFreeMinutes() < 0)
                || (p.getDetentionBlockMinutes() != null && p.getDetentionBlockMinutes() <= 0))
            throw new BadRequestException("Detention free/block minutes are invalid");
        if ("PERCENT_REVENUE".equals(p.getPayMethod()) && (p.getRevenueBasis()==null || !java.util.Set.of("INVOICE_SUBTOTAL","PRIMARY_INVOICE_REVENUE","NET_ELIGIBLE_REVENUE").contains(p.getRevenueBasis())))
            throw new BadRequestException("REVENUE_BASIS_UNAVAILABLE", "Explicit versioned PRIMARY/NET revenue basis required; INVOICE_SUBTOTAL is legacy primary-only compatibility");
        boolean valid = switch (p.getPayMethod()) {
            case "PER_MILE" -> p.getPerMileRate() != null && p.getMileageBasis() != null;
            case "PER_LOAD" -> p.getPerLoadRate() != null;
            case "PERCENT_REVENUE" -> p.getRevenuePercentage() != null && p.getRevenueBasis() != null;
            case "HOURLY" -> p.getHourlyRate() != null;
            case "DAILY" -> p.getDailyRate() != null;
            case "FLAT_RATE" -> p.getFlatRate() != null;
            default -> false;
        };
        if (!valid) throw new BadRequestException("The selected pay method requires its rate and basis fields");
    }

    private void requirePrecision(java.math.BigDecimal value, int scale) {
        if (value != null && value.stripTrailingZeros().scale() > scale)
            throw new BadRequestException("PAY_RATE_PRECISION", "Pay rate exceeds persisted decimal precision");
    }

    private String mileageBasis(String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "ACTUAL", "ACTUAL_MILES", "ACTUAL_ALL_MILES" -> "ACTUAL_ALL_MILES";
            case "PLANNED", "PLANNED_MILES", "PLANNED_ALL_MILES" -> "PLANNED_ALL_MILES";
            default -> throw new BadRequestException("MILEAGE_BASIS_UNAVAILABLE", "Only explicit assignment actual/planned all miles have an attributable driver source");
        };
    }
}
