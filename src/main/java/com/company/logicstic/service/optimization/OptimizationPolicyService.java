package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.*;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.OptimizationAuditRepository;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.PublishedPolicy;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.Policy;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OptimizationPolicyService {
    private final OptimizationAuditRepository audit;
    private final EmployeeRepository employees;
    public record PublishRequest(String code, int version, Policy eligibilitySourcePolicy,
                                 OptimizationScoringPolicy scoringPolicy, String approvalReference) {}
    @Transactional
    public PublishedPolicy publish(PublishRequest request, UUID actor) {
        if (actor == null || !employees.existsById(actor)) throw new ForbiddenException("Optimization policy actor must map to a tenant employee");
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 100
                || request.version() <= 0 || request.eligibilitySourcePolicy() == null || request.scoringPolicy() == null
                || request.approvalReference() == null || request.approvalReference().isBlank()) {
            throw new BadRequestException("INVALID_OPTIMIZATION_POLICY", "Complete explicit published and approved policy required");
        }
        audit.lockCommand("OPTIMIZATION_POLICY", request.code() + ":" + request.version());
        var existing = audit.policy(request.code(), request.version());
        if (existing.isPresent()) {
            PublishedPolicy p = existing.get();
            if (!p.eligibilitySourcePolicy().equals(request.eligibilitySourcePolicy()) || !p.scoringPolicy().equals(request.scoringPolicy())
                    || !p.approvalReference().equals(request.approvalReference())) {
                throw new ApiException(HttpStatus.CONFLICT, "OPTIMIZATION_POLICY_IMMUTABLE", "Publish a new version; existing policy cannot be changed");
            }
            return p;
        }
        var policy = new PublishedPolicy(UUID.randomUUID(), request.code(), request.version(), request.eligibilitySourcePolicy(), request.scoringPolicy(),
                request.approvalReference(), actor, Instant.now().truncatedTo(ChronoUnit.MICROS));
        audit.insert(policy);
        return policy;
    }
    @Transactional(readOnly = true)
    public PublishedPolicy get(UUID id) { return audit.policy(id).orElseThrow(() -> new ResourceNotFoundException("Published optimization policy not found")); }
}
