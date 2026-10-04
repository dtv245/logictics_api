package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.repository.OptimizationQualifiedInputRepository.Ledger;
import com.company.logicstic.service.optimization.OptimizationForecastResolver.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.*;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audited source capture. Financial amounts come only from locked approved ledger rows. */
@Service @RequiredArgsConstructor
public class OptimizationQualifiedInputService {
    private final OptimizationQualifiedInputRepository inputs;
    private final OptimizationAuditRepository audit;
    private final OptimizationPolicyService policies;
    private final EmployeeRepository employees;
    private final OptimizationEvidenceValidator validator;
    private final RatingFingerprintService fingerprints;
    public record WeightRequest(BigDecimal value, String unit) {}
    public record CapacityRequest(WeightRequest cargo, WeightRequest truck) {}
    // Nullable booleans distinguish an explicit false from an omitted business assertion.
    public record QualificationRequest(Boolean qualifiedDriver, Boolean validLicense, Boolean hazmatDriver,
                                       Boolean equipmentMatches, Boolean hazmatTruck, Boolean operational,
                                       Boolean maintenanceClear, Boolean hazmatRequired, Instant effectiveFrom, Instant effectiveUntil) {}
    public record CostRequest(UUID costId, String forecastPolicyCode, int forecastPolicyVersion, String zeroCostReason) {}
    public record ForecastRequest(Boolean accessorialApplicable, Boolean permitApplicable, List<CostRequest> costs) {}
    public record CaptureRequest(UUID id, UUID policyId, Kind kind, Scope scope, Source source, String unit,
                                 Instant observedAt, Instant expiresAt, Long maxAgeSeconds, UUID supersedesInputId,
                                 CapacityRequest capacity, QualificationRequest qualification, ForecastRequest forecast,
                                 String approvalReference, String reasonCode, String reason) {}

    @Transactional
    public Captured capture(CaptureRequest request, UUID actor) {
        if (actor == null || !employees.existsById(actor)) throw new ForbiddenException("Qualified source actor must map to tenant employee");
        basic(request);
        CaptureRequest normalized = normalize(request);
        String hash = fingerprints.hash(normalized);
        audit.lockCommand("OPTIMIZATION_QUALIFIED_INPUT", request.id().toString());
        var existing = inputs.find(request.id());
        if (existing.isPresent()) {
            if (!hash.equals(existing.get().normalizedInputHash())) throw new ConflictException("OPTIMIZATION_INPUT_IDEMPOTENCY_CONFLICT", "Same evidence identity cannot be reused for different input");
            return existing.get();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var policy = policies.get(request.policyId()).eligibilitySourcePolicy();
        if (!inputs.contextExists(request.scope())) throw new ResourceNotFoundException("Qualified candidate context not found in this tenant");
        CandidateContext context = context(request.scope(), now);
        Provenance proof = new Provenance(request.source(), context, request.id().toString(), "1", request.unit(), request.observedAt(), request.expiresAt(), request.maxAgeSeconds());
        List<String> errors = validator.validate(new Input<>(request, proof), request.kind(), context, policy, now);
        if (!errors.isEmpty()) throw invalid(errors.getFirst(), "Complete fresh registered source evidence required");
        Payload payload = payload(normalized, actor, now);
        int version = 1;
        if (request.supersedesInputId() != null) {
            audit.lockCommand("OPTIMIZATION_SOURCE_CORRECTION",request.supersedesInputId().toString());
            Captured prior = get(request.supersedesInputId());
            if (inputs.superseded(prior.id())) throw new ConflictException("OPTIMIZATION_INPUT_ALREADY_SUPERSEDED", "Source correction must append to current evidence");
            if (!prior.scope().equals(request.scope()) || prior.kind() != request.kind()) throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID", "Correction cannot change candidate or input kind");
            version = prior.evidenceVersion() + 1;
        }
        Captured result = new Captured(request.id(), request.policyId(), request.kind(), request.scope(), request.source(), request.unit(),
                request.observedAt(), request.expiresAt(), request.maxAgeSeconds(), version, request.supersedesInputId(), payload,
                request.approvalReference(), request.reasonCode(), request.reason(), actor, now, hash);
        inputs.insert(result);
        return result;
    }
    @Transactional(readOnly=true)
    public Captured get(UUID id) { return inputs.find(id).orElseThrow(() -> new ResourceNotFoundException("Qualified optimization input not found")); }

    /** Used by run/accept: original payload is immutable, but live ledger/version and supersession are revalidated. */
    @Transactional(readOnly=true)
    public Captured resolve(UUID id, UUID policyId, Kind kind, CandidateContext context, Instant evaluatedAt) {
        Captured input = get(id);
        if (!input.policyId().equals(policyId) || input.kind()!=kind || !input.scope().matches(context))
            throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID", "Evidence must belong to selected policy, kind and exact candidate");
        if (inputs.superseded(id)) throw new ConflictException("OPTIMIZATION_CANDIDATE_STALE", "Qualified source evidence was explicitly superseded");
        var policy = policies.get(policyId).eligibilitySourcePolicy();
        List<String> errors = validator.validate(input.bind(input.payload(),context),kind,context,policy,evaluatedAt);
        if (!errors.isEmpty()) throw invalid(errors.getFirst(),"Qualified source evidence is missing or stale");
        if (kind==Kind.FORECAST_COST) {
            List<Cost> costs = input.payload().forecast().costs();
            Map<UUID,Ledger> live = ledgerById(costs.stream().map(Cost::costId).toList(),false);
            for (Cost cost : costs) {
                Ledger row = live.get(cost.costId());
                if (row==null || !eligible(row,input.scope(),evaluatedAt) || row.version()!=cost.ledgerVersion()
                        || !row.category().equals(cost.category().name()) || !row.currency().equals(cost.currency())
                        || row.amount().compareTo(cost.amount())!=0 || !row.approvedBy().equals(cost.approvedBy())
                        || !row.approvedAt().equals(cost.approvedAt()))
                    throw new ConflictException("OPTIMIZATION_CANDIDATE_STALE", "Approved candidate forecast ledger changed since source capture");
            }
        }
        return input;
    }
    private Payload payload(CaptureRequest r, UUID actor, Instant now) {
        return switch(r.kind()) {
            case CAPACITY -> {
                if (!"POUND".equals(r.unit()) || r.capacity()==null || r.qualification()!=null || r.forecast()!=null
                        || r.capacity().cargo()==null || r.capacity().truck()==null) throw invalid("CAPACITY_EVIDENCE_UNAVAILABLE","Explicit cargo/truck values and original units required");
                yield new Payload(new Capacity(validator.weight(r.capacity().cargo().value(),r.capacity().cargo().unit()),
                        validator.weight(r.capacity().truck().value(),r.capacity().truck().unit())),null,null);
            }
            case QUALIFICATION -> {
                var q=r.qualification();
                if (!"QUALIFICATION".equals(r.unit()) || r.source().classification()!=SourceClass.AUTHORITATIVE_DB || q==null || r.capacity()!=null || r.forecast()!=null
                        || q.qualifiedDriver()==null || q.validLicense()==null || q.hazmatDriver()==null || q.equipmentMatches()==null
                        || q.hazmatTruck()==null || q.operational()==null || q.maintenanceClear()==null || q.hazmatRequired()==null
                        || q.effectiveFrom()==null || q.effectiveUntil()==null || !q.effectiveUntil().isAfter(q.effectiveFrom()))
                    throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID","All qualification assertions and explicit effective interval required");
                yield new Payload(null,new Qualification(q.qualifiedDriver(),q.validLicense(),q.hazmatDriver(),q.equipmentMatches(),q.hazmatTruck(),
                        q.operational(),q.maintenanceClear(),q.hazmatRequired(),q.effectiveFrom(),q.effectiveUntil()),null);
            }
            case FORECAST_COST -> new Payload(null,null,forecast(r,actor,now));
            default -> throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID","Only authoritative capacity, qualification and approved forecasts may be captured");
        };
    }
    private Forecast forecast(CaptureRequest r, UUID actor, Instant now) {
        ForecastRequest f=r.forecast();
        if (r.capacity()!=null || r.qualification()!=null || f==null || f.accessorialApplicable()==null || f.permitApplicable()==null
                || f.costs()==null || f.costs().isEmpty() || !r.unit().matches("[A-Z]{3}")) throw incomplete();
        Set<Category> required=new HashSet<>(Set.of(Category.FUEL,Category.DRIVER,Category.TOLL));
        if(f.accessorialApplicable()) required.add(Category.ACCESSORIAL);
        if(f.permitApplicable()) required.add(Category.PERMIT);
        Map<UUID,Ledger> rows=ledgerById(f.costs().stream().map(CostRequest::costId).toList(),true);
        var costs=new ArrayList<Cost>(); var categories=new HashSet<Category>();
        for(CostRequest request:f.costs()) {
            Ledger row=rows.get(request.costId());
            if(row==null || !eligible(row,r.scope(),now) || blank(request.forecastPolicyCode()) || request.forecastPolicyVersion()<=0) throw incomplete();
            Category category;
            try { category=Category.valueOf(row.category()); } catch(IllegalArgumentException e){throw incomplete();}
            if(!required.contains(category)) throw incomplete();
            if(!r.unit().equals(row.currency())) throw invalid("OPTIMIZATION_CURRENCY_MISMATCH","Qualified forecast costs must use exact candidate currency");
            ZeroCostEvidence zero=null;
            if(row.amount().signum()==0) {
                if(blank(request.zeroCostReason())) throw incomplete();
                zero=new ZeroCostEvidence("ZERO_COST_CONFIRMED",request.zeroCostReason(),actor,now);
            } else if(!blank(request.zeroCostReason())) throw incomplete();
            costs.add(new Cost(row.id(),row.version(),category,row.basis(),row.status(),row.currency(),row.amount(),row.approvedBy(),row.approvedAt(),
                    request.forecastPolicyCode(),request.forecastPolicyVersion(),zero));
            categories.add(category);
        }
        if(!categories.containsAll(required)) throw incomplete();
        return new Forecast(f.accessorialApplicable(),f.permitApplicable(),costs);
    }
    private Map<UUID,Ledger> ledgerById(List<UUID> ids, boolean lock) {
        if(ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size()) throw incomplete();
        var result=new HashMap<UUID,Ledger>(); inputs.ledger(ids,lock).forEach(row->result.put(row.id(),row)); return result;
    }
    private static boolean eligible(Ledger r, Scope s, Instant at) {
        return r.loadId().equals(s.loadId()) && (r.tripId()==null || r.tripId().equals(s.tripId()))
                && (r.driverId()==null || r.driverId().equals(s.driverId())) && (r.truckId()==null || r.truckId().equals(s.truckId()))
                && "ESTIMATE".equals(r.basis()) && "APPROVED".equals(r.status()) && r.approvedBy()!=null && r.approvedAt()!=null
                && !r.approvedAt().isAfter(at) && r.amount()!=null && r.amount().signum()>=0;
    }
    private static CaptureRequest normalize(CaptureRequest r) {
        ForecastRequest f=r.forecast();
        if(f!=null) {
            if(f.costs()==null || f.costs().stream().anyMatch(c->c==null || c.costId()==null)) throw incomplete();
            f=new ForecastRequest(f.accessorialApplicable(),f.permitApplicable(),f.costs().stream().sorted(Comparator.comparing(CostRequest::costId)).toList());
        }
        return new CaptureRequest(r.id(),r.policyId(),r.kind(),r.scope(),r.source(),r.unit(),r.observedAt(),r.expiresAt(),r.maxAgeSeconds(),
                r.supersedesInputId(),r.capacity(),r.qualification(),f,r.approvalReference(),r.reasonCode(),r.reason());
    }
    private static void basic(CaptureRequest r) {
        if(r==null || r.id()==null || r.policyId()==null || r.kind()==null || !Set.of(Kind.CAPACITY,Kind.QUALIFICATION,Kind.FORECAST_COST).contains(r.kind())
                || r.scope()==null || r.scope().loadId()==null || r.scope().tripId()==null || r.scope().driverId()==null || r.scope().truckId()==null
                || r.source()==null || blank(r.unit()) || blank(r.approvalReference()) || blank(r.reason()) || r.reasonCode()==null
                || !r.reasonCode().matches("[A-Z][A-Z0-9_]{0,79}")) throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID","Explicit candidate/source/approval/correction audit required");
    }
    private static CandidateContext context(Scope s, Instant at) { return new CandidateContext(s.loadId(),s.tripId(),s.driverId(),s.truckId(),at,at.plusSeconds(72*3600)); }
    private static boolean blank(String s) { return s==null || s.isBlank(); }
    private static BadRequestException invalid(String code,String message) { return new BadRequestException(code,message); }
    private static BadRequestException incomplete() { return invalid("FORECAST_COST_INCOMPLETE","Complete approved candidate-specific estimate coverage and audited zero required"); }
}
