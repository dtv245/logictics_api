package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.rating.RatingPreviewRequest;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.rating.domain.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RatingInputLoader {
    private final LoadRepository loads;
    private final LoadRatingContextService dates;
    private final RateRuleResolver rules;
    private final RatingMileageService mileage;
    private final AccessorialChargeRepository charges;

    @Transactional(readOnly = true)
    public RatingInputs load(UUID id, RatingPreviewRequest request) {
        if (request == null || request.contextSource() == null || request.contextSource().isBlank()
                || request.contextSource().length() > 2000 || request.accessorialIds() == null)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Explicit dimension provenance and accessorial selection required");
        for (String d : new String[]{request.lane(),request.equipment(),request.service(),request.tier()})
            if (d != null && d.isBlank()) throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Configured context dimensions cannot be blank");
        var load = loads.findById(id).orElseThrow(() -> new ResourceNotFoundException("Load not found"));
        var pricingDate = dates.pricingDate(id);
        var context = new RateMatchContext(load.getCustomer().getId(),request.contractId(),request.contractVersion(),
                request.lane(),request.equipment(),request.service(),request.tier(),CurrencyGuard.canonical(request.currency()),pricingDate.pricingDate());
        var rule = rules.resolve(context);
        ResolvedRatingMileage linehaul = null, fuel = null;
        if (rule.method() == RatingMethod.PER_MILE)
            linehaul = mileage.resolve(id,RatingMileageComponent.LINEHAUL,rule.linehaulMileageBasis(),request.linehaulMileageEvidenceId(),rule);
        else if (request.linehaulMileageEvidenceId() != null)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "FLAT linehaul does not consume mileage");
        if (rule.fsc() != null)
            fuel = mileage.resolve(id,RatingMileageComponent.FSC,rule.fsc().mileageBasis(),request.fscMileageEvidenceId(),rule);
        else if (request.fscMileageEvidenceId() != null)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Rule has no FSC policy");
        List<RatingAccessorialInput> accessorials = new ArrayList<>(); var seen = new HashSet<UUID>();
        for (UUID chargeId : request.accessorialIds()) {
            if (chargeId == null || !seen.add(chargeId)) throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Duplicate/null accessorial selection");
            var c = charges.findById(chargeId).orElseThrow(() -> new BadRequestException("RATING_VALIDATION_REQUIRED", "Selected accessorial not found"));
            if (c.getLoad() == null || !id.equals(c.getLoad().getId()) || !"APPROVED".equals(c.getStatus())
                    || c.getApprovedBy() == null || c.getApprovedAt() == null || c.getVersion() == null)
                throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Accessorial must belong to Load and have audited approval, not be already invoiced");
            if (rule.fsc() != null && "FUEL_SURCHARGE".equals(c.getType()))
                throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Manual fuel charge and computed FSC require explicit reconciliation; cannot double bill");
            CurrencyGuard.requireSameCurrency(rule.currency(),c.getCurrency());
            if (c.getCustomerAmount() == null || c.getCustomerAmount().signum() < 0)
                throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Invalid approved customer amount");
            accessorials.add(new RatingAccessorialInput(c.getId(),id,c.getType(),c.getStatus(),c.getQuantity(),c.getUnit(),c.getRate(),
                    c.getCustomerAmount(),c.getCurrency(),c.getApprovedBy(),c.getApprovedAt(),c.getVersion()));
        }
        accessorials.sort(java.util.Comparator.comparing(a -> a.chargeId().toString()));
        return new RatingInputs(id,pricingDate,context,request.contextSource(),rule,linehaul,fuel,accessorials);
    }
}
