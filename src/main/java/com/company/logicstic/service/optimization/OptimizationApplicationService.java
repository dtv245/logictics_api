package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.*;
import com.company.logicstic.integration.hos.HosFeasibilityService;
import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.integration.optimization.OptimizationInputProvider;
import com.company.logicstic.repository.*;
import com.company.logicstic.repository.OptimizationCandidateRepository.*;
import com.company.logicstic.service.optimization.OptimizationForecastResolver.*;
import com.company.logicstic.service.optimization.OptimizationQualifiedInputBatchResolver.Resolved;
import com.company.logicstic.service.optimization.OptimizationScoringEngine.*;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** External forecasts are resolved outside transactions; only DB-owned immutable audit is committed together. */
@Service @RequiredArgsConstructor @Slf4j
public class OptimizationApplicationService {
    private final OptimizationAuditRepository audit;
    private final OptimizationCandidateRepository candidates;
    private final OptimizationPolicyService policies;
    private final OptimizationQualifiedInputBatchResolver sources;
    private final OptimizationInputProvider dynamic;
    private final HosFeasibilityService hos;
    private final OptimizationHardFeasibilityService feasibility;
    private final OptimizationForecastResolver forecasts;
    private final OptimizationScoringEngine scores;
    private final OptimizationTenantScope tenants;
    private final EmployeeRepository employees;
    private final RatingFingerprintService fingerprints;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private static final int MAX_CANDIDATES=200; // Explicit API work bound; never silently truncate or sample.
    public record CreateRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=200) String idempotencyKey,@jakarta.validation.constraints.NotNull UUID policyId,@jakarta.validation.constraints.NotEmpty List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid Target> targets,@jakarta.validation.constraints.NotEmpty List<@jakarta.validation.constraints.NotNull UUID> driverIds,@jakarta.validation.constraints.NotEmpty List<@jakarta.validation.constraints.NotNull UUID> truckIds,@jakarta.validation.constraints.NotNull List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid SourceSelection> sourceSelections) {}
    public record Prepared(Explanation explanation,String materialFingerprint) {}
    private record DbInputs(Map<Scope,State> states,Map<Scope,Resolved> resolved) {}

    public Outcome create(CreateRequest body,UUID actor,String correlationId) {
        if(actor==null || !employees.existsById(actor)) throw new ForbiddenException("Optimization actor must map to tenant employee");
        RunRequest request=normalize(body,tenants.require());String commandHash=fingerprints.hash(request);
        var previous=readTransaction().execute(s->audit.request(request.idempotencyKey()));
        if(previous.isPresent())return replay(previous.get(),commandHash);
        PublishedPolicy policy=policies.get(request.policyId());
        Instant createdAt=now(),until=createdAt.plusSeconds(72*3600);long started=System.nanoTime();UUID runId=UUID.randomUUID();
        List<RequestCandidate> scope=scope(request);Map<Scope,CandidateContext> contexts=contexts(scope,createdAt,until);
        DbInputs initial=readTransaction().execute(s->load(policy,request,scope,contexts,now()));
        Map<Scope,Prepared> prepared=new LinkedHashMap<>();
        for(RequestCandidate item:scope) {
            Scope candidate=item.scope();prepared.put(candidate,calculate(contexts.get(candidate),initial.states().get(candidate),
                    initial.resolved().get(candidate),policy));
        }
        String correlation=correlationId==null || correlationId.isBlank() || correlationId.length()>200?UUID.randomUUID().toString():correlationId;
        Outcome outcome=writeTransaction().execute(s->{
            audit.lockCommand("OPTIMIZATION_RUN",request.idempotencyKey());
            var existing=audit.request(request.idempotencyKey());if(existing.isPresent())return replay(existing.get(),commandHash);
            Instant calculatedAt=now();DbInputs current=load(policy,request,scope,contexts,calculatedAt);
            List<Candidate> results=new ArrayList<>();
            for(RequestCandidate item:scope) {
                Scope key=item.scope();Prepared p=prepared.get(key);Explanation e=p.explanation();State state=current.states().get(key);
                var reasons=new ArrayList<>(e.rejectionCodes());
                if(!e.databaseStateFingerprint().equals(fingerprints.hash(state)))reasons.add("OPTIMIZATION_CANDIDATE_STALE");
                Resolved resolved=current.resolved().get(key);if(resolved!=null)reasons.addAll(resolved.rejectionCodes());
                reasons.addAll(feasibility.evaluate(contexts.get(key),state.facts(),e.evidence(),e.hos(),policy.eligibilitySourcePolicy(),calculatedAt).rejectionCodes());
                List<String> distinct=reasons.stream().distinct().toList();boolean feasible=distinct.isEmpty() && e.score()!=null;
                Explanation finalExplanation=new Explanation(e.context(),e.facts(),e.evidence(),e.hos(),e.forecast(),feasible,distinct,feasible?e.score():null,e.databaseStateFingerprint());
                UUID id=UUID.nameUUIDFromBytes((runId+":"+scopeKey(key)).getBytes(StandardCharsets.UTF_8));
                results.add(new Candidate(id,runId,key.loadId(),key.tripId(),key.driverId(),key.truckId(),e.facts().acceptedRatingSnapshotId(),
                        feasible,distinct,feasible?e.score().finalScore():null,null,materialFingerprint(finalExplanation),finalExplanation));
            }
            Map<UUID,Integer> ranks=new HashMap<>();scores.denseRanks(results.stream().filter(Candidate::feasible).map(c->new CandidateScore(c.id(),c.explanation().score())).toList()).forEach(c->ranks.put(c.candidateId(),c.rank()));
            List<Candidate> ranked=results.stream().map(c->new Candidate(c.id(),c.runId(),c.loadId(),c.tripId(),c.driverId(),c.truckId(),c.ratingSnapshotId(),c.feasible(),
                    c.rejectionCodes(),c.finalScore(),ranks.get(c.id()),c.inputFingerprint(),c.explanation())).toList();
            Run run=new Run(runId,policy.id(),createdAt,until,actor,request.idempotencyKey(),commandHash,request,new PolicySnapshot(policy.eligibilitySourcePolicy(),policy.scoringPolicy()),
                    calculatedAt,Math.max(0,(System.nanoTime()-started)/1_000_000),correlation);
            audit.insert(run);ranked.forEach(audit::insert);return new Outcome(run,audit.candidates(runId));
        });
        log.info("optimization_run policyId={} policyVersion={} correlationId={} durationMillis={} feasibleCandidates={} candidates={}",policy.id(),policy.version(),correlation,
                outcome.run().durationMillis(),outcome.candidates().stream().filter(Candidate::feasible).count(),outcome.candidates().size());
        return outcome;
    }
    public Outcome get(UUID id) {
        return readTransaction().execute(s->{Run run=audit.run(id).orElseThrow(()->new ResourceNotFoundException("Optimization run not found"));return new Outcome(run,audit.candidates(id));});
    }
    /** Acceptance uses the same source/HOS/domain path, not a second reduced feasibility implementation. */
    public Prepared revalidate(Run run,Candidate candidate) {
        if(!Objects.equals(tenants.require(),run.requestSnapshot().tenantScope()))throw new ResourceNotFoundException("Optimization run not found in this tenant scope");
        PublishedPolicy policy=policies.get(run.policyId());Scope key=new Scope(candidate.loadId(),candidate.tripId(),candidate.driverId(),candidate.truckId());
        Target target=run.requestSnapshot().targets().stream().filter(t->t.loadId().equals(key.loadId()) && t.tripId().equals(key.tripId())).findFirst().orElseThrow(()->new ResourceNotFoundException("Candidate target not found"));
        List<RequestCandidate> scope=List.of(new RequestCandidate(key,target));Map<Scope,CandidateContext> contexts=contexts(scope,run.createdAt(),run.planningUntil());
        if(!now().isBefore(run.planningUntil()))throw new ConflictException("OPTIMIZATION_CANDIDATE_STALE","Optimization planning interval has elapsed");
        List<SourceSelection> selected=run.requestSnapshot().sourceSelections().stream().filter(s->s.scope().equals(key)).toList();
        RunRequest request=new RunRequest(run.idempotencyKey(),policy.id(),List.of(target),List.of(key.driverId()),List.of(key.truckId()),selected,run.requestSnapshot().tenantScope());
        DbInputs current=readTransaction().execute(s->load(policy,request,scope,contexts,now()));
        return calculate(contexts.get(key),current.states().get(key),current.resolved().get(key),policy);
    }
    private DbInputs load(PublishedPolicy policy,RunRequest request,List<RequestCandidate> scope,Map<Scope,CandidateContext> contexts,Instant at) {
        Map<Scope,State> states=candidates.states(scope,contexts.values().iterator().next().planningStart(),contexts.values().iterator().next().planningEnd());
        if(states.size()!=scope.size())throw new ResourceNotFoundException("Candidate Load/Trip/Driver/Truck not found in this tenant");
        return new DbInputs(states,sources.resolve(policy,request.sourceSelections(),contexts,at));
    }
    private Prepared calculate(CandidateContext context,State state,Resolved resolved,PublishedPolicy policy) {
        var reasons=new ArrayList<String>();OptimizationInputProvider.DynamicEvidence dynamicEvidence=null;Input<Assessment> assessment=null;
        if(resolved==null)reasons.add("OPTIMIZATION_INPUT_EVIDENCE_REQUIRED");else reasons.addAll(resolved.rejectionCodes());
        if(state.facts().sameTenantAndTripLoadContext() && state.facts().loadPreDispatch() && state.facts().tripPreDispatch()
                && !state.facts().conflictingActiveAssignment() && state.facts().routingLocationsAvailable() && state.facts().acceptedRatingSnapshotId()!=null
                && state.facts().requestedPickupBusinessDate()!=null && resolved!=null && resolved.rejectionCodes().isEmpty()
                && Arrays.stream(EntityKind.values()).allMatch(k->state.facts().statuses().get(k)!=null
                    && policy.eligibilitySourcePolicy().statusAllowlists().get(k).contains(state.facts().statuses().get(k)))) {
            try {dynamicEvidence=dynamic.fetch(context,state.routing());}catch(ApiException ex){reasons.add(ex.getCode());}
            if(dynamicEvidence!=null)try {assessment=hos.evaluate(context,dynamicEvidence.route());}catch(ApiException ex){reasons.add(ex.getCode());}
        }
        Bundle bundle=new Bundle(dynamicEvidence==null?null:dynamicEvidence.location(),dynamicEvidence==null?null:dynamicEvidence.driverAvailability(),
                dynamicEvidence==null?null:dynamicEvidence.truckAvailability(),resolved==null?null:resolved.capacity(),resolved==null?null:resolved.qualification(),dynamicEvidence==null?null:dynamicEvidence.route());
        Instant evaluatedAt=now();
        reasons.addAll(feasibility.evaluate(context,state.facts(),bundle,assessment,policy.eligibilitySourcePolicy(),evaluatedAt).rejectionCodes());
        if(bundle.qualification()!=null && bundle.qualification().value().hazmatRequired()!=state.hazmatRequired())reasons.add("HAZMAT_EVIDENCE_CONTEXT_MISMATCH");
        if(state.routing().appointmentStart()==null)reasons.add("ON_TIME_UTILITY_UNAVAILABLE");
        else if(state.routing().appointmentStart().isBefore(context.planningStart()) || state.routing().appointmentStart().isAfter(context.planningEnd()))reasons.add("PICKUP_OUTSIDE_PLANNING_HORIZON");
        Result forecast=null;Score score=null;
        if(resolved!=null && resolved.forecast()!=null && state.revenue()!=null)try {
            var f=resolved.forecast();var payload=f.payload().forecast();List<Input<Cost>> costs=payload.costs().stream().map(c->f.bind(c,context)).toList();
            forecast=forecasts.resolve(context,state.revenue(),costs,Map.of(Category.ACCESSORIAL,payload.accessorialApplicable(),Category.PERMIT,payload.permitApplicable()),policy.eligibilitySourcePolicy(),evaluatedAt);
        } catch(ApiException ex){reasons.add(ex.getCode());}
        else reasons.add("FORECAST_COST_INCOMPLETE");
        if(reasons.isEmpty())try {
            Route r=bundle.route().value();score=scores.score(new Inputs(true,r.deadhead().normalizedMiles(),r.loadAttributedLoadedMiles().normalizedMiles(),forecast.expectedRevenue(),forecast.expectedVariableCost(),
                    r.appointmentStart(),r.predictedArrivalAtPickup(),assessment.value().minimumHosHeadroomRatio()),policy.scoringPolicy());
        }catch(ApiException ex){reasons.add(ex.getCode());}
        List<String> distinct=reasons.stream().distinct().toList();
        Explanation explanation=new Explanation(context,state.facts(),bundle,assessment,forecast,distinct.isEmpty() && score!=null,distinct,score,fingerprints.hash(state));
        return new Prepared(explanation,materialFingerprint(explanation));
    }
    public String materialFingerprint(Explanation explanation) {
        JsonNode tree=json.valueToTree(explanation);removeRetrievalInstants(tree);return fingerprints.hash(tree);
    }
    private static void removeRetrievalInstants(JsonNode node) {
        if(node.isObject()) {
            JsonNode provenance=node.get("provenance");if(provenance instanceof ObjectNode proof){proof.remove("observedAt");proof.remove("expiresAt");}
            node.properties().forEach(e->removeRetrievalInstants(e.getValue()));
        } else if(node.isArray())node.forEach(OptimizationApplicationService::removeRetrievalInstants);
    }
    private Outcome replay(Run existing,String hash) {
        if(!existing.normalizedInputHash().equals(hash))throw new ConflictException("OPTIMIZATION_IDEMPOTENCY_CONFLICT","Same run key cannot be reused for different normalized scope/policy");
        return new Outcome(existing,audit.candidates(existing.id()));
    }
    public static RunRequest normalize(CreateRequest r,String tenant) {
        if(r==null || r.idempotencyKey()==null || r.idempotencyKey().isBlank() || !r.idempotencyKey().equals(r.idempotencyKey().trim()) || r.idempotencyKey().length()>200
                || r.policyId()==null || r.targets()==null || r.targets().isEmpty() || r.driverIds()==null || r.driverIds().isEmpty() || r.truckIds()==null || r.truckIds().isEmpty()
                || r.sourceSelections()==null || r.targets().stream().anyMatch(t->t==null || t.loadId()==null || t.tripId()==null || t.ratingSnapshotId()==null || t.pickupStopId()==null)
                || r.driverIds().stream().anyMatch(Objects::isNull) || r.truckIds().stream().anyMatch(Objects::isNull))throw invalid();
        if((long)r.targets().size()*r.driverIds().size()*r.truckIds().size()>MAX_CANDIDATES)throw new BadRequestException("OPTIMIZATION_SCOPE_TOO_LARGE","Explicit scope exceeds 200 candidate API work bound; no scope is truncated");
        if(new HashSet<>(r.driverIds()).size()!=r.driverIds().size() || new HashSet<>(r.truckIds()).size()!=r.truckIds().size()
                || r.targets().stream().map(t->t.loadId()+":"+t.tripId()).distinct().count()!=r.targets().size())throw invalid();
        for(SourceSelection s:r.sourceSelections())if(s==null || s.scope()==null || s.capacityInputId()==null || s.qualificationInputId()==null || s.forecastInputId()==null
                || !r.driverIds().contains(s.scope().driverId()) || !r.truckIds().contains(s.scope().truckId())
                || r.targets().stream().noneMatch(t->t.loadId().equals(s.scope().loadId()) && t.tripId().equals(s.scope().tripId())))throw invalid();
        if(r.sourceSelections().stream().map(SourceSelection::scope).distinct().count()!=r.sourceSelections().size())throw invalid();
        return new RunRequest(r.idempotencyKey(),r.policyId(),r.targets().stream().sorted(Comparator.comparing(t->t.loadId()+":"+t.tripId())).toList(),
                r.driverIds().stream().sorted().toList(),r.truckIds().stream().sorted().toList(),r.sourceSelections().stream().sorted(Comparator.comparing(s->scopeKey(s.scope()))).toList(),tenant);
    }
    public static List<RequestCandidate> scope(RunRequest request) {
        var result=new ArrayList<RequestCandidate>();for(Target t:request.targets())for(UUID d:request.driverIds())for(UUID v:request.truckIds())result.add(new RequestCandidate(new Scope(t.loadId(),t.tripId(),d,v),t));return List.copyOf(result);
    }
    private static Map<Scope,CandidateContext> contexts(List<RequestCandidate> scope,Instant start,Instant end) {
        var result=new LinkedHashMap<Scope,CandidateContext>();scope.forEach(c->result.put(c.scope(),new CandidateContext(c.scope().loadId(),c.scope().tripId(),c.scope().driverId(),c.scope().truckId(),start,end)));return result;
    }
    private TransactionTemplate readTransaction(){var t=new TransactionTemplate(transactions);t.setReadOnly(true);t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);return t;}
    private TransactionTemplate writeTransaction(){return new TransactionTemplate(transactions);}
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static String scopeKey(Scope s){return s.loadId()+":"+s.tripId()+":"+s.driverId()+":"+s.truckId();}
    private static BadRequestException invalid(){return new BadRequestException("INVALID_OPTIMIZATION_SCOPE","Explicit distinct candidate scope, accepted rating, pickup stop and source selections required");}
}
