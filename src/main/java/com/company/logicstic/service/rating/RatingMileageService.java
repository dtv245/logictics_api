package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.rating.ContractMileageRequest;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.rating.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RatingMileageService {
    private final RatingMileageRepository mileages;
    private final LoadRepository loads;
    private final EmployeeRepository employees;
    private final RatePolicyService policies;
    private final LoadRatingContextService dates;

    @Transactional
    public ContractMileageEvidence capture(UUID loadId, ContractMileageRequest r, UUID actor) {
        ContractMileageValidator.validate(r);
        if (actor == null || !employees.existsById(actor)) throw new BadRequestException("RATING_ACTOR_REQUIRED", "Persisted authenticated employee actor required");
        var load = loads.findByIdForUpdate(loadId).orElseThrow(() -> new ResourceNotFoundException("Load not found"));
        var date = dates.pricingDate(loadId).pricingDate();
        var c = policies.getContract(r.contractId(), r.contractVersion());
        validateContext(load.getCustomer().getId(), date, c);
        var e = new ContractMileageEvidence(UUID.randomUUID(), loadId, r.componentType(), c.contractId(), c.version(),
                c.currency(), r.originalValue(), r.originalUnit(), r.originalValue(), r.provenance(), actor, OffsetDateTime.now(ZoneOffset.UTC));
        mileages.capture(e);
        return mileages.evidence(e.id()).orElseThrow(() -> new ResourceNotFoundException("Captured mileage evidence not found"));
    }

    @Transactional(readOnly = true)
    public ResolvedRatingMileage resolve(UUID loadId, RatingMileageComponent component, RatingMileageBasis basis, UUID evidenceId, RateRule rule) {
        if (component == null || rule == null) throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Component and selected published rule required");
        if (component == RatingMileageComponent.MINIMUM_CHARGE) {
            if (basis != null || evidenceId != null) throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Monetary minimum has no mileage inputs");
            return new ResolvedRatingMileage(component, "NOT_APPLICABLE", null, null, null, null, null, null, null, null, null, null);
        }
        if (basis == null) throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Explicit component mileage basis required");
        if (component == RatingMileageComponent.LINEHAUL && !Objects.equals(rule.linehaulMileageBasis(), basis)
                || component == RatingMileageComponent.FSC && (rule.fsc() == null || rule.fsc().mileageBasis() != basis))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Selected basis disagrees with the component's versioned policy");
        var load = loads.findById(loadId).orElseThrow(() -> new ResourceNotFoundException("Load not found"));
        var date = dates.pricingDate(loadId).pricingDate();
        if (basis != RatingMileageBasis.CONTRACT_MILES) {
            // V23 has no qualified Load route or movement-leg source. Never allocate Trip totals.
            if (mileages.hasMultiLoadTrip(loadId))
                throw new BadRequestException("RATE_MILEAGE_ATTRIBUTION_REQUIRED", "Explicit Load mileage attribution is required for a multi-load Trip");
            throw new BadRequestException("RATE_MILEAGE_UNAVAILABLE", "No authoritative Load-level source for " + basis);
        }
        if (evidenceId == null) throw new BadRequestException("RATE_MILEAGE_UNAVAILABLE", "Explicit contract mileage evidence ID required");
        var e = mileages.evidence(evidenceId).orElseThrow(() -> new BadRequestException("RATE_MILEAGE_UNAVAILABLE", "Contract mileage evidence unavailable"));
        if (!e.loadId().equals(loadId) || e.componentType() != component)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Mileage evidence belongs to another Load/component");
        var c = policies.getContract(e.contractId(), e.contractVersion());
        validateContext(load.getCustomer().getId(), date, c);
        CurrencyGuard.requireSameCurrency(rule.currency(), e.currency());
        if (rule.customerId() != null && !rule.customerId().equals(load.getCustomer().getId())
                || rule.contractId() != null && (!rule.contractId().equals(e.contractId()) || !Objects.equals(rule.contractVersion(), e.contractVersion())))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Contract mileage disagrees with selected rule customer/contract version");
        return new ResolvedRatingMileage(component, basis.name(), "CONTRACT", e.contractId().toString(), e.contractVersion(),
                e.originalValue(), e.originalUnit(), e.normalizedMiles(), e.id(), e.provenance(), e.capturedBy(), e.capturedAt());
    }
    private void validateContext(UUID customer, java.time.LocalDate date, RatingContract c) {
        if (!c.customerId().equals(customer) || date.isBefore(c.effectiveFrom()) || c.effectiveTo() != null && date.isAfter(c.effectiveTo()))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Contract customer/effective date must agree with Load business date");
    }
}
