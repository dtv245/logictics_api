package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.rating.RateRuleRequest;
import com.company.logicstic.dto.rating.RatingContractRequest;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.RatePolicyRepository;
import com.company.logicstic.service.rating.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RatePolicyService {
    private final RatePolicyRepository policies;

    @Transactional
    public RatingContract createContract(RatingContractRequest request, UUID actor) {
        RatePolicyValidator.contract(request); actor(actor); customer(request.customerId());
        UUID id = UUID.randomUUID(); var at = OffsetDateTime.now(ZoneOffset.UTC);
        policies.createContractIdentity(id, request.customerId(), actor, at);
        policies.appendContract(id, 1, request, CurrencyGuard.canonical(request.currency()), actor, at);
        return getContract(id, 1);
    }
    @Transactional
    public RatingContract newContractVersion(UUID id, int expectedVersion, RatingContractRequest request, UUID actor) {
        RatePolicyValidator.contract(request); actor(actor);
        if (!policies.lockContract(id)) throw new ResourceNotFoundException("Rating contract not found");
        requireLatest(expectedVersion, policies.latestContractVersion(id));
        if (!getContract(id, expectedVersion).customerId().equals(request.customerId()))
            throw new BadRequestException("INVALID_RATE_POLICY", "Contract customer identity cannot change; create another contract");
        policies.appendContract(id, expectedVersion + 1, request, CurrencyGuard.canonical(request.currency()), actor, OffsetDateTime.now(ZoneOffset.UTC));
        return getContract(id, expectedVersion + 1);
    }
    @Transactional
    public RateRule createRule(RateRuleRequest request, UUID actor) {
        validateRuleContext(request, actor);
        UUID id = UUID.randomUUID(); var at = OffsetDateTime.now(ZoneOffset.UTC);
        policies.createRuleIdentity(id, actor, at);
        policies.appendRule(id, 1, request, CurrencyGuard.canonical(request.currency()), actor, at);
        return getRule(id, 1);
    }
    @Transactional
    public RateRule newRuleVersion(UUID id, int expectedVersion, RateRuleRequest request, UUID actor) {
        validateRuleContext(request, actor);
        if (!policies.lockRule(id)) throw new ResourceNotFoundException("Rate rule not found");
        requireLatest(expectedVersion, policies.latestRuleVersion(id));
        policies.appendRule(id, expectedVersion + 1, request, CurrencyGuard.canonical(request.currency()), actor, OffsetDateTime.now(ZoneOffset.UTC));
        return getRule(id, expectedVersion + 1);
    }
    @Transactional(readOnly = true)
    public RatingContract getContract(UUID id, int version) {
        return policies.contract(id, version).orElseThrow(() -> new ResourceNotFoundException("Rating contract version not found"));
    }
    @Transactional(readOnly = true)
    public RateRule getRule(UUID id, int version) {
        return policies.rule(id, version).orElseThrow(() -> new ResourceNotFoundException("Rate rule version not found"));
    }
    private void validateRuleContext(RateRuleRequest r, UUID actor) {
        RatePolicyValidator.rule(r); actor(actor);
        if (r.customerId() != null) customer(r.customerId());
        if (r.contractId() == null) return;
        var c = getContract(r.contractId(), r.contractVersion());
        CurrencyGuard.requireSameCurrency(c.currency(), r.currency());
        if ((r.customerId() != null && !r.customerId().equals(c.customerId()))
                || r.effectiveFrom().isBefore(c.effectiveFrom())
                || (c.effectiveTo() != null && (r.effectiveTo() == null || r.effectiveTo().isAfter(c.effectiveTo()))))
            throw new BadRequestException("INVALID_RATE_POLICY", "Rule scope/period disagrees with its contract version");
    }
    private void actor(UUID actor) {
        if (actor == null || !policies.actorExists(actor))
            throw new BadRequestException("RATING_ACTOR_REQUIRED", "Persisted authenticated employee actor required");
    }
    private void customer(UUID customer) {
        if (!policies.customerExists(customer)) throw new ResourceNotFoundException("Customer not found");
    }
    private void requireLatest(int expected, int latest) {
        if (expected <= 0 || expected != latest)
            throw new ApiException(HttpStatus.CONFLICT, "RATE_POLICY_VERSION_STALE", "Append from the current version; history cannot be rewritten");
    }
}
