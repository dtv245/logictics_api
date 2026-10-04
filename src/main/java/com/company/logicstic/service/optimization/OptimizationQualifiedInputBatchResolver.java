package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.OptimizationQualifiedInputRepository;
import com.company.logicstic.repository.OptimizationQualifiedInputRepository.Ledger;
import com.company.logicstic.service.optimization.OptimizationForecastResolver.Cost;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Constant query count for scoped candidates: evidence, supersession and live ledger are bulk reads. */
@Component @RequiredArgsConstructor
public class OptimizationQualifiedInputBatchResolver {
    private final OptimizationQualifiedInputRepository repository;
    private final OptimizationEvidenceValidator validator;
    public record Resolved(Input<Capacity> capacity,Input<Qualification> qualification,Captured forecast,List<String> rejectionCodes) {
        public Resolved { rejectionCodes=List.copyOf(rejectionCodes); }
    }
    public Map<Scope,Resolved> resolve(PublishedPolicy policy,List<SourceSelection> selections,Map<Scope,CandidateContext> contexts,Instant at) {
        var ids=new HashSet<UUID>();for(var s:selections){ids.add(s.capacityInputId());ids.add(s.qualificationInputId());ids.add(s.forecastInputId());}
        var captured=new HashMap<UUID,Captured>();repository.findAll(ids).forEach(i->captured.put(i.id(),i));
        if(captured.size()!=ids.size()) throw new ResourceNotFoundException("Selected qualified evidence not found in this tenant");
        Set<UUID> superseded=repository.superseded(ids);
        var costIds=new HashSet<UUID>();for(Captured i:captured.values()) if(i.kind()==Kind.FORECAST_COST) i.payload().forecast().costs().forEach(c->costIds.add(c.costId()));
        var ledger=new HashMap<UUID,Ledger>();repository.ledger(costIds.stream().sorted().toList(),false).forEach(r->ledger.put(r.id(),r));
        var result=new HashMap<Scope,Resolved>();
        for(var selection:selections) {
            var errors=new ArrayList<String>();CandidateContext context=contexts.get(selection.scope());
            Captured capacity=checked(captured.get(selection.capacityInputId()),Kind.CAPACITY,policy,context,at,superseded,errors);
            Captured qualification=checked(captured.get(selection.qualificationInputId()),Kind.QUALIFICATION,policy,context,at,superseded,errors);
            Captured forecast=checked(captured.get(selection.forecastInputId()),Kind.FORECAST_COST,policy,context,at,superseded,errors);
            if(forecast!=null) for(Cost c:forecast.payload().forecast().costs()) {
                Ledger r=ledger.get(c.costId());Scope scope=selection.scope();
                if(r==null || !scope.loadId().equals(r.loadId()) || r.tripId()!=null && !scope.tripId().equals(r.tripId())
                        || r.driverId()!=null && !scope.driverId().equals(r.driverId()) || r.truckId()!=null && !scope.truckId().equals(r.truckId())
                        || !"ESTIMATE".equals(r.basis()) || !"APPROVED".equals(r.status()) || r.version()!=c.ledgerVersion()
                        || r.amount().compareTo(c.amount())!=0 || !r.currency().equals(c.currency()) || !r.category().equals(c.category().name())
                        || !Objects.equals(r.approvedBy(),c.approvedBy()) || !Objects.equals(r.approvedAt(),c.approvedAt())
                        || r.approvedAt()==null || r.approvedAt().isAfter(at)) errors.add("OPTIMIZATION_CANDIDATE_STALE");
            }
            result.put(selection.scope(),new Resolved(capacity==null?null:capacity.bind(capacity.payload().capacity(),context),
                    qualification==null?null:qualification.bind(qualification.payload().qualification(),context),forecast,errors.stream().distinct().toList()));
        }
        return Map.copyOf(result);
    }
    private Captured checked(Captured input,Kind kind,PublishedPolicy policy,CandidateContext context,Instant at,Set<UUID> superseded,List<String> errors) {
        if(!input.policyId().equals(policy.id()) || input.kind()!=kind || !input.scope().matches(context)) {errors.add("OPTIMIZATION_INPUT_EVIDENCE_INVALID");return null;}
        if(superseded.contains(input.id()))errors.add("OPTIMIZATION_CANDIDATE_STALE");
        errors.addAll(validator.validate(input.bind(input.payload(),context),kind,context,policy.eligibilitySourcePolicy(),at));
        return input;
    }
}
