package com.company.logicstic.service.payroll;

import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.DriverPayPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DriverPayPolicyResolver {
    private final DriverPayPolicyRepository policies;

    public DriverPayPolicy resolve(UUID driverId, LocalDate workDate) {
        if (driverId == null || workDate == null) throw new BadRequestException("POLICY_INPUT_REQUIRED", "Driver and work date are required");
        var specific = effective(policies.findDriverPoliciesAt(driverId, workDate), workDate);
        if (!specific.isEmpty()) return only(specific);
        var defaults = effective(policies.findDefaultPoliciesAt(workDate), workDate);
        if (!defaults.isEmpty()) return only(defaults);
        throw new BadRequestException("DRIVER_PAY_POLICY_UNAVAILABLE", "No effective driver pay policy on " + workDate);
    }

    private List<DriverPayPolicy> effective(List<DriverPayPolicy> started, LocalDate date) {
        Map<String, DriverPayPolicy> latestByCode = new LinkedHashMap<>();
        // Repositories order newest effective date/version first. An expired newer version
        // must not resurrect its still-open historical predecessor.
        for (var policy : started) latestByCode.putIfAbsent(policy.getPolicyCode(), policy);
        return latestByCode.values().stream().filter(p -> p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(date)).toList();
    }
    private DriverPayPolicy only(List<DriverPayPolicy> candidates) {
        if (candidates.size() != 1) throw new BadRequestException("DRIVER_PAY_POLICY_AMBIGUOUS", "Multiple effective policy families require an explicit policy selection");
        return candidates.getFirst();
    }
}
