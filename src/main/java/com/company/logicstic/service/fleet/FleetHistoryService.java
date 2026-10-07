package com.company.logicstic.service.fleet;

import com.company.logicstic.common.MetricDto;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.fleet.FleetHistory.*;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor @Slf4j
public class FleetHistoryService {
    private final FleetHistoryRepository history;private final EmployeeRepository employees;
    private final RatingFingerprintService fingerprints;private final FleetIntervalCalculator calculator;
    public record Publish(@jakarta.validation.constraints.NotBlank String code,
                          @jakarta.validation.constraints.Min(1) int version,
                          @jakarta.validation.constraints.NotNull Map<String,Boolean> membershipStates,
                          @jakarta.validation.constraints.NotNull Map<String,Boolean> capacityStates,
                          @jakarta.validation.constraints.NotNull Map<String,Activity> activityStates,
                          @jakarta.validation.constraints.NotNull List<Source> sources,
                          @jakarta.validation.constraints.NotBlank String approvalReference) {}
    public record Capture(UUID policyId,
                          @jakarta.validation.constraints.NotNull UUID truckId,
                          @jakarta.validation.constraints.NotNull Kind kind,
                          @jakarta.validation.constraints.NotBlank String status,
                          @jakarta.validation.constraints.NotNull Instant occurredAt,
                          @jakarta.validation.constraints.NotNull Instant validUntil,
                          @jakarta.validation.constraints.NotNull Source source,
                          String sourceEventId,UUID tripId,UUID loadId,String executionReference,
                          UUID supersedesEventId,String reasonCode,String reason) {}
    public record Attribute(UUID policyId,
                            @jakarta.validation.constraints.NotNull UUID truckId,
                            @jakarta.validation.constraints.NotNull UUID tripId,
                            @jakarta.validation.constraints.NotNull Instant completedAt,
                            BigDecimal loadedMiles,
                            BigDecimal emptyMiles,
                            @jakarta.validation.constraints.NotNull BigDecimal actualMiles,
                            @jakarta.validation.constraints.NotNull Source source,
                            String sourceEventId,
                            UUID supersedesId,String reasonCode,String reason) {}
    @Transactional public Policy publish(Publish r,UUID actor) {
        actor(actor);if(r==null || blank(r.code()) || r.code().length()>100 || r.version()<1 || blank(r.approvalReference())
            || !mapping(r.membershipStates()) || !mapping(r.capacityStates()) || !mapping(r.activityStates())
            || r.sources()==null || r.sources().isEmpty() || r.sources().stream().anyMatch(s->!sourceValid(s)) || new HashSet<>(r.sources()).size()!=r.sources().size())throw invalid("Explicit policy maps, qualified sources and approval required");
        var normalized=new Publish(r.code(),r.version(),r.membershipStates(),r.capacityStates(),r.activityStates(),r.sources().stream().sorted(Comparator.comparing(Source::type).thenComparing(Source::reference).thenComparing(Source::version)).toList(),r.approvalReference());
        history.lock("POLICY:"+r.code()+":"+r.version());var prior=history.policy(r.code(),r.version());
        if(prior.isPresent()) {var p=prior.get();var old=new Publish(p.code(),p.version(),p.membershipStates(),p.capacityStates(),p.activityStates(),p.sources(),p.approvalReference());if(!fingerprints.hash(normalized).equals(fingerprints.hash(old)))throw conflict("FLEET_POLICY_IMMUTABLE");return p;}
        var p=new Policy(UUID.randomUUID(),r.code(),r.version(),r.membershipStates(),r.capacityStates(),r.activityStates(),normalized.sources(),r.approvalReference(),actor,now());history.insert(p);return p;
    }
    public Policy policy(UUID id){return history.policy(id).orElseThrow(()->new ResourceNotFoundException("Fleet policy not found in tenant"));}
    @Transactional public Event capture(Capture r,UUID actor) {
        actor(actor);if(r==null || r.truckId()==null || r.kind()==null || blank(r.status()) || r.occurredAt()==null || r.validUntil()==null
            || !r.occurredAt().isBefore(r.validUntil()) || !micro(r.occurredAt()) || !micro(r.validUntil()) || blank(r.sourceEventId()) || blank(r.reasonCode()) || blank(r.reason()))throw invalid("Explicit bounded interval/source/status/reason required");
        var p=policy(r.policyId());qualified(p,r.source());truck(r.truckId());String hash=fingerprints.hash(r);
        history.lock(identity(r.source(),r.sourceEventId()));var prior=history.event(r.source(),r.sourceEventId());
        if(prior.isPresent()){if(!hash.equals(prior.get().normalizedInputHash()))throw conflict("FLEET_EVENT_IDEMPOTENCY_CONFLICT");return prior.get();}
        if(!history.executionContext(r.tripId(),r.loadId()))throw new ResourceNotFoundException("Fleet execution context not found in tenant");
        if(r.kind()==Kind.ACTIVITY && r.occurredAt().isAfter(now()))throw invalid("Actual activity cannot begin after capture time");
        String classification=switch(r.kind()) {
            case MEMBERSHIP -> !p.membershipStates().containsKey(r.status())?"UNAVAILABLE":p.membershipStates().get(r.status())?"IN":"OUT";
            case CAPACITY -> !p.capacityStates().containsKey(r.status())?"UNAVAILABLE":p.capacityStates().get(r.status())?"AVAILABLE":"EXCLUDED";
            case ACTIVITY -> p.activityStates().getOrDefault(r.status(),Activity.UNAVAILABLE).name();
        };
        if(classification.equals("PRODUCTIVE") && ((r.tripId()==null && r.loadId()==null) || blank(r.executionReference())))throw invalid("Productive activity requires actual execution identity/reference");
        if(r.supersedesEventId()!=null) {
            history.lock("EVENT_CORRECTION:"+r.supersedesEventId());var parent=history.event(r.supersedesEventId()).orElseThrow(()->new ResourceNotFoundException("Fleet correction parent not found"));
            if(!parent.policyId().equals(p.id()) || !parent.truckId().equals(r.truckId()) || parent.kind()!=r.kind() || history.supersededEvent(parent.id()))throw conflict("FLEET_CORRECTION_CONFLICT");
        }
        var e=new Event(UUID.randomUUID(),p.id(),r.truckId(),r.kind(),r.status(),classification,r.occurredAt(),r.validUntil(),r.source(),r.sourceEventId(),r.tripId(),r.loadId(),r.executionReference(),r.supersedesEventId(),r.reasonCode(),r.reason(),actor,now(),hash);history.insert(e);return e;
    }
    @Transactional public Mileage attribute(Attribute r,UUID actor) {
        actor(actor);if(r==null || r.truckId()==null || r.tripId()==null || r.completedAt()==null || !micro(r.completedAt())
            || !miles(r.loadedMiles()) || !miles(r.emptyMiles()) || !miles(r.actualMiles()) || r.loadedMiles().add(r.emptyMiles()).compareTo(r.actualMiles())>0
            || blank(r.sourceEventId()) || blank(r.reasonCode()) || blank(r.reason()))throw invalid("Explicit actual completion mileage and proven truck/source audit required");
        var p=policy(r.policyId());qualified(p,r.source());truck(r.truckId());String hash=fingerprints.hash(r);
        history.lock(identity(r.source(),r.sourceEventId()));var prior=history.mileage(r.source(),r.sourceEventId());
        if(prior.isPresent()){if(!hash.equals(prior.get().normalizedInputHash()))throw conflict("FLEET_EVENT_IDEMPOTENCY_CONFLICT");return prior.get();}
        history.lock("TRIP_ATTRIBUTION:"+r.tripId());var actual=history.actualTrip(r.tripId()).orElseThrow(()->new ResourceNotFoundException("Mileage Trip not found in tenant"));
        if(actual.cancelled() || !Objects.equals(actual.completedAt(),r.completedAt()) || r.completedAt().isAfter(now()) || !decimal(actual.loaded(),r.loadedMiles()) || !decimal(actual.empty(),r.emptyMiles()) || !decimal(actual.actual(),r.actualMiles()))throw conflict("FLEET_MILEAGE_SOURCE_STALE");
        if(r.supersedesId()==null && history.originalMileageExists(r.tripId()))throw conflict("FLEET_MILEAGE_ALREADY_ATTRIBUTED");
        if(r.supersedesId()!=null) {
            var parent=history.mileage(r.supersedesId()).orElseThrow(()->new ResourceNotFoundException("Mileage correction parent not found"));
            if(!parent.policyId().equals(p.id()) || !parent.tripId().equals(r.tripId()) || history.supersededMileage(parent.id()))throw conflict("FLEET_CORRECTION_CONFLICT");
        }
        var m=new Mileage(UUID.randomUUID(),p.id(),r.truckId(),r.tripId(),r.completedAt(),r.loadedMiles().setScale(3),r.emptyMiles().setScale(3),r.actualMiles().setScale(3),r.source(),r.sourceEventId(),r.supersedesId(),r.reasonCode(),r.reason(),actor,now(),hash);history.insert(m);return m;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Report report(UUID policyId,List<UUID> truckIds,Instant from,Instant to,LocalDate first,LocalDate exclusiveLast,String zone,String correlationId) {
        long started=System.nanoTime();var p=policy(policyId);
        if(truckIds==null || truckIds.isEmpty() || truckIds.size()>200 || truckIds.stream().anyMatch(Objects::isNull) || new HashSet<>(truckIds).size()!=truckIds.size())throw invalid("Explicit distinct truck scope required (maximum 200); no implicit current fleet");
        var scope=truckIds.stream().sorted().toList();if(!history.trucksExist(scope))throw new ResourceNotFoundException("Fleet truck scope not found in tenant");
        var period=calculator.period(from,to,first,exclusiveLast,zone);Instant calculated=now();if(period.to().isAfter(calculated))throw invalid("Historical fleet report cannot claim future actual utilization");
        var coverage=history.durations(p.id(),scope,period);var utilization=calculator.utilization(coverage);var miles=history.mileageTotals(p.id(),scope,period);
        String unavailable=miles.missing()>0?"FLEET_MILEAGE_ATTRIBUTION_REQUIRED":miles.count()==0?"ACTUAL_MILEAGE_HISTORY_UNAVAILABLE":null;
        MetricDto loaded=unavailable==null?calculator.ratio("LOADED_MILES_PERCENT",miles.loaded(),miles.loaded().add(miles.empty()),"ACTUAL_LOADED_PLUS_EMPTY_MILES"):
            MetricDto.unavailable("LOADED_MILES_PERCENT","PERCENT",unavailable);
        MetricDto deadhead=unavailable==null?calculator.ratio("DEADHEAD_PERCENT",miles.empty(),miles.actual(),"ACTUAL_OPERATIONAL_MILES"):
            MetricDto.unavailable("DEADHEAD_PERCENT","PERCENT",unavailable);
        // Historical interval/classification/full cost coverage is unqualified; approved unavailable outcomes, never fake zero.
        var health=List.of(MetricDto.unavailable("UNPLANNED_DOWNTIME","SECOND","DOWNTIME_INTERVAL_SOURCE_UNAVAILABLE"),
            MetricDto.unavailable("PM_COMPLIANCE","PERCENT","HISTORICAL_PM_DUE_OCCURRENCES_UNAVAILABLE"),
            MetricDto.unavailable("MAINTENANCE_COST_PER_MILE","MONEY_PER_MILE","COMPLETE_MAINTENANCE_COST_COVERAGE_UNAVAILABLE"),
            MetricDto.unavailable("BREAKDOWNS_PER_100K_MILES","COUNT_PER_100K_MILES","BREAKDOWN_CLASSIFICATION_UNAVAILABLE"));
        log.info("fleet_report policyCode={} policyVersion={} reportingPolicy={} correlationId={} durationMillis={} availability={} reason={}",p.code(),p.version(),FleetIntervalCalculator.CODE,correlationId,(System.nanoTime()-started)/1_000_000,utilization.availability(),utilization.reason());
        return new Report(p.id(),p.code(),p.version(),FleetIntervalCalculator.CODE,1,period,scope,coverage,utilization,loaded,deadhead,health,calculated);
    }
    private void actor(UUID id){if(id==null || !employees.existsById(id))throw new ForbiddenException("Fleet actor must map to authenticated tenant employee");}
    private void truck(UUID id){if(!history.trucksExist(List.of(id)))throw new ResourceNotFoundException("Fleet truck not found in tenant");}
    private static boolean mapping(Map<?,?> m){return m!=null && !m.isEmpty() && m.entrySet().stream().noneMatch(e->e.getKey()==null || blank(e.getKey().toString()) || e.getValue()==null);}
    private static boolean sourceValid(Source s){return s!=null && !blank(s.type()) && !blank(s.reference()) && !blank(s.version());}
    private static void qualified(Policy p,Source s){if(!sourceValid(s) || !p.sources().contains(s))throw invalid("Source identity/version must be explicitly registered in policy");}
    private static String identity(Source s,String id){return s.type()+":"+s.reference()+":"+s.version()+":"+id;}
    private static boolean blank(String s){return s==null || s.isBlank();}
    private static boolean micro(Instant i){return i.getNano()%1000==0;}
    private static boolean miles(BigDecimal n){return n!=null && n.signum()>=0 && n.stripTrailingZeros().scale()<=3 && n.compareTo(new BigDecimal("999999999.999"))<=0;}
    private static boolean decimal(BigDecimal a,BigDecimal b){return a!=null && a.compareTo(b)==0;}
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static BadRequestException invalid(String message){return new BadRequestException("FLEET_VALIDATION_REQUIRED",message);}
    private static ConflictException conflict(String code){return new ConflictException(code,"Fleet evidence identity/context changed; explicit audited correction required");}
}
