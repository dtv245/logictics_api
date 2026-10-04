package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.repository.OptimizationAcceptanceRepository.Accepted;
import com.company.logicstic.repository.OptimizationCandidateRepository.RequestCandidate;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service @RequiredArgsConstructor @Slf4j
public class OptimizationAcceptanceService {
    private final OptimizationApplicationService optimization;
    private final OptimizationAcceptanceRepository acceptances;
    private final OptimizationAuditRepository audit;
    private final OptimizationCandidateRepository database;
    private final OptimizationQualifiedInputBatchResolver sources;
    private final OptimizationPolicyService policies;
    private final OptimizationHardFeasibilityService feasibility;
    private final OptimizationTenantScope tenants;
    private final EmployeeRepository employees;
    private final RatingFingerprintService fingerprints;
    private final PlatformTransactionManager transactions;
    public record AcceptRequest(String idempotencyKey,String expectedInputFingerprint) {}
    public Accepted accept(UUID runId,UUID candidateId,AcceptRequest request,UUID actor) {
        if(actor==null || !employees.existsById(actor))throw new ForbiddenException("Acceptance actor must map to tenant employee");
        if(request==null || request.idempotencyKey()==null || request.idempotencyKey().isBlank() || request.idempotencyKey().length()>200
                || !request.idempotencyKey().equals(request.idempotencyKey().trim()) || request.expectedInputFingerprint()==null
                || !request.expectedInputFingerprint().matches("[0-9a-f]{64}"))throw new BadRequestException("INVALID_OPTIMIZATION_ACCEPTANCE","Explicit command key and selected candidate fingerprint required");
        String hash=fingerprints.hash(Map.of("runId",runId,"candidateId",candidateId,"expectedInputFingerprint",request.expectedInputFingerprint()));
        // Retry/alias journal is checked before any live external call. Each key keeps its own immutable hash/outcome.
        Accepted replay=transaction().execute(s->replay(runId,candidateId,request,hash,actor));if(replay!=null)return replay;
        Outcome outcome=optimization.get(runId);Run run=outcome.run();
        if(!Objects.equals(tenants.require(),run.requestSnapshot().tenantScope()))throw new ResourceNotFoundException("Optimization run not found in this tenant scope");
        Candidate candidate=outcome.candidates().stream().filter(c->c.id().equals(candidateId)).findFirst().orElseThrow(()->new ResourceNotFoundException("Optimization candidate not found"));
        if(!candidate.feasible())throw new ConflictException("OPTIMIZATION_INFEASIBLE","Only a feasible audited candidate can be accepted");
        if(!candidate.inputFingerprint().equals(request.expectedInputFingerprint()) || run.requestSnapshot().sourceSelections()==null)throw stale();
        OptimizationApplicationService.Prepared current=optimization.revalidate(run,candidate); // Routing/full-HOS outside the write transaction.
        if(!current.explanation().feasible() || !current.materialFingerprint().equals(candidate.inputFingerprint()))throw stale();
        Scope scope=new Scope(candidate.loadId(),candidate.tripId(),candidate.driverId(),candidate.truckId());
        Target target=run.requestSnapshot().targets().stream().filter(t->t.loadId().equals(scope.loadId()) && t.tripId().equals(scope.tripId())).findFirst().orElseThrow(OptimizationAcceptanceService::stale);
        SourceSelection selection=run.requestSnapshot().sourceSelections().stream().filter(s->s.scope().equals(scope)).findFirst().orElseThrow(OptimizationAcceptanceService::stale);
        var policy=policies.get(run.policyId());
        Accepted accepted=transaction().execute(s->{
            Accepted prior=replay(runId,candidateId,request,hash,actor);if(prior!=null)return prior;
            acceptances.lockResources(scope,run,target,selection);
            // Serialize different run/command keys claiming the same Load/Driver/Truck.
            prior=replay(runId,candidateId,request,hash,actor);if(prior!=null)return prior;
            if(acceptances.load(scope.loadId()).isPresent())throw stale();
            Instant at=now();if(!at.isBefore(run.planningUntil()))throw stale();
            var states=database.states(List.of(new RequestCandidate(scope,target)),run.createdAt(),run.planningUntil());var state=states.get(scope);
            if(state==null || !fingerprints.hash(state).equals(current.explanation().databaseStateFingerprint()))throw stale();
            var resolved=sources.resolve(policy,List.of(selection),Map.of(scope,current.explanation().context()),at).get(scope);
            if(resolved==null || !resolved.rejectionCodes().isEmpty() || !feasibility.evaluate(current.explanation().context(),state.facts(),current.explanation().evidence(),
                    current.explanation().hos(),policy.eligibilitySourcePolicy(),at).feasible())throw stale();
            UUID assignment=acceptances.assign(scope,run,actor,at);
            Accepted result=new Accepted(UUID.randomUUID(),runId,candidateId,scope.loadId(),scope.tripId(),scope.driverId(),scope.truckId(),assignment,
                    candidate.inputFingerprint(),current.explanation(),actor,at);
            acceptances.insert(result);acceptances.recordCommand(request.idempotencyKey(),hash,result,actor,at);return result;
        });
        log.info("optimization_accepted runId={} candidateId={} policyVersion={} correlationId={} assignmentId={}",runId,candidateId,policy.version(),run.correlationId(),accepted.driverAssignmentId());
        return accepted;
    }
    private Accepted replay(UUID runId,UUID candidateId,AcceptRequest request,String hash,UUID actor) {
        audit.lockCommand("OPTIMIZATION_ACCEPT",request.idempotencyKey());
        var command=acceptances.command(request.idempotencyKey());
        if(command.isPresent()) {
            if(!command.get().normalizedInputHash().equals(hash))throw new ConflictException("OPTIMIZATION_IDEMPOTENCY_CONFLICT","Same acceptance key cannot be reused for different input");
            return command.get().outcome();
        }
        var existing=acceptances.run(runId);
        if(existing.isPresent()) {
            if(!existing.get().candidateId().equals(candidateId))throw new ConflictException("OPTIMIZATION_ALREADY_ACCEPTED","Run already accepted a different candidate; no implicit replacement");
            if(!existing.get().originalInputFingerprint().equals(request.expectedInputFingerprint()))throw stale();
            acceptances.recordCommand(request.idempotencyKey(),hash,existing.get(),actor,now());return existing.get();
        }
        return null;
    }
    private TransactionTemplate transaction(){return new TransactionTemplate(transactions);}
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static ConflictException stale(){return new ConflictException("OPTIMIZATION_CANDIDATE_STALE","Candidate inputs or availability changed; explicitly rerun optimization");}
}
