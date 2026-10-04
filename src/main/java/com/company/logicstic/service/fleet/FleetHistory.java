package com.company.logicstic.service.fleet;

import com.company.logicstic.common.MetricDto;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit authored history; never a projection of current truck status. */
public final class FleetHistory {
    private FleetHistory() {}
    public enum Kind { MEMBERSHIP, CAPACITY, ACTIVITY }
    public enum Activity { PRODUCTIVE, NON_PRODUCTIVE, EXCLUDED, UNAVAILABLE }
    public record Source(String type,String reference,String version) {}
    public record Policy(UUID id,String code,int version,Map<String,Boolean> membershipStates,
                         Map<String,Boolean> capacityStates,Map<String,Activity> activityStates,
                         List<Source> sources,String approvalReference,UUID publishedBy,Instant publishedAt) {
        public Policy {
            membershipStates=Map.copyOf(membershipStates);capacityStates=Map.copyOf(capacityStates);
            activityStates=Map.copyOf(activityStates);sources=List.copyOf(sources);
        }
    }
    public record Event(UUID id,UUID policyId,UUID truckId,Kind kind,String status,String classification,
                        Instant occurredAt,Instant validUntil,Source source,String sourceEventId,
                        UUID tripId,UUID loadId,String executionReference,UUID supersedesEventId,
                        String reasonCode,String reason,UUID capturedBy,Instant capturedAt,String normalizedInputHash) {}
    public record Mileage(UUID id,UUID policyId,UUID truckId,UUID tripId,Instant completedAt,BigDecimal loadedMiles,
                          BigDecimal emptyMiles,BigDecimal actualMiles,Source source,String sourceEventId,
                          UUID supersedesId,String reasonCode,String reason,UUID capturedBy,Instant capturedAt,String normalizedInputHash) {}
    public record Durations(UUID truckId,BigDecimal scopeSeconds,BigDecimal membershipSeconds,
                            BigDecimal capacitySeconds,BigDecimal productiveSeconds,BigDecimal gapSeconds,
                            BigDecimal conflictSeconds,long eventCount) {}
    public record Period(Instant from,Instant to,String businessZoneId) {}
    public record Report(UUID policyId,String policyCode,int policyVersion,String reportingPolicyCode,int reportingPolicyVersion,
                         Period period,List<UUID> truckIds,List<Durations> coverage,MetricDto utilization,
                         MetricDto loadedMilesPercent,MetricDto deadheadPercent,List<MetricDto> health,Instant calculatedAt) {
        public Report {truckIds=List.copyOf(truckIds);coverage=List.copyOf(coverage);health=List.copyOf(health);}
    }
}
