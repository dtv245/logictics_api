package com.company.logicstic.repository;

import com.company.logicstic.integration.optimization.OptimizationInputProvider.RoutingContext;
import com.company.logicstic.service.optimization.OptimizationForecastResolver.Revenue;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.Target;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Bounded query projection over actual resources/assignments, never the full tenant history. */
@Repository @RequiredArgsConstructor
public class OptimizationCandidateRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record RequestCandidate(Scope scope,Target target) {}
    public record State(Scope scope,Facts facts,RoutingContext routing,Revenue revenue,boolean hazmatRequired,
                        UUID assignedTruckId,String activeAssignmentsJson,String otherTruckTripsJson) {}
    public Map<Scope,State> states(List<RequestCandidate> candidates,Instant start,Instant end) {
        if(candidates.isEmpty()) return Map.of();
        List<State> rows=jdbc.query("""
                with scope as (select value from jsonb_array_elements(?::jsonb)),
                period as (select ?::timestamptz start_at,?::timestamptz end_at)
                select l.id load_id,t.id trip_id,d.id driver_id,v.id truck_id,
                    l.status load_status,t.status trip_status,d.status driver_status,v.status truck_status,
                    l.requested_pickup_business_date,l.is_hazmat,t.truck_id assigned_truck_id,
                    (s.id is not null) context_matches,
                    (l.dispatched_at is null and l.picked_up_at is null and l.delivered_at is null and l.cancelled_at is null) load_pre_dispatch,
                    (t.dispatched_at is null and t.completed_at is null and t.cancelled_at is null
                     and not exists(select 1 from trip_stops begun where begun.trip_id=t.id and
                       (begun.arrived_at is not null or begun.service_started_at is not null or begun.service_completed_at is not null or begun.departed_at is not null))) trip_pre_dispatch,
                    l.origin_location_latitude,l.origin_location_longitude,l.destination_location_latitude,l.destination_location_longitude,
                    s.id pickup_stop_id,s.appointment_start,s.appointment_end,r.id rating_id,r.subtotal,r.currency,
                    ((t.truck_id is not null and t.truck_id<>v.id)
                     or exists(select 1 from trip_driver_assignments a where a.effective_from<period.end_at and (a.effective_to is null or a.effective_to>period.start_at)
                        and ((a.driver_id=d.id and a.trip_id<>t.id) or (a.trip_id=t.id and a.driver_id<>d.id)))
                     or exists(select 1 from trips other where other.truck_id=v.id and other.id<>t.id and other.completed_at is null and other.cancelled_at is null)
                     or exists(select 1 from trip_stops other_stop join trips other on other.id=other_stop.trip_id
                        where other_stop.load_id=l.id and other.id<>t.id and other.completed_at is null and other.cancelled_at is null
                        and (other.truck_id is not null or exists(select 1 from trip_driver_assignments a where a.trip_id=other.id
                            and a.effective_from<period.end_at and (a.effective_to is null or a.effective_to>period.start_at))))) assignment_conflict,
                    coalesce((select jsonb_agg(jsonb_build_object('id',a.id,'tripId',a.trip_id,'driverId',a.driver_id,'type',a.assignment_type,
                        'effectiveFrom',a.effective_from,'effectiveTo',a.effective_to) order by a.id)::text
                        from trip_driver_assignments a where (a.driver_id=d.id or a.trip_id=t.id) and a.effective_from<period.end_at
                        and (a.effective_to is null or a.effective_to>period.start_at)),'[]') active_assignments,
                    coalesce((select jsonb_agg(jsonb_build_object('tripId',other.id,'status',other.status,'dispatchedAt',other.dispatched_at) order by other.id)::text
                        from trips other where other.truck_id=v.id and other.id<>t.id and other.completed_at is null and other.cancelled_at is null),'[]') other_truck_trips
                from scope cross join period
                join loads l on l.id=(scope.value #>> '{scope,loadId}')::uuid
                join trips t on t.id=(scope.value #>> '{scope,tripId}')::uuid
                join employees d on d.id=(scope.value #>> '{scope,driverId}')::uuid
                join trucks v on v.id=(scope.value #>> '{scope,truckId}')::uuid
                left join trip_stops s on s.id=(scope.value #>> '{target,pickupStopId}')::uuid and s.trip_id=t.id and s.load_id=l.id and s.type='PICKUP'
                left join accepted_rating_snapshots r on r.id=(scope.value #>> '{target,ratingSnapshotId}')::uuid and r.load_id=l.id
                """,(rs,n)->{
            Scope scope=new Scope(rs.getObject("load_id",UUID.class),rs.getObject("trip_id",UUID.class),rs.getObject("driver_id",UUID.class),rs.getObject("truck_id",UUID.class));
            var statuses=new EnumMap<EntityKind,String>(EntityKind.class);
            for(var entry:Map.of(EntityKind.LOAD,"load_status",EntityKind.TRIP,"trip_status",EntityKind.DRIVER,"driver_status",EntityKind.TRUCK,"truck_status").entrySet()) {
                String value=rs.getString(entry.getValue());if(value!=null)statuses.put(entry.getKey(),value);
            }
            Location origin=new Location(rs.getBigDecimal("origin_location_latitude"),rs.getBigDecimal("origin_location_longitude"));
            Location destination=new Location(rs.getBigDecimal("destination_location_latitude"),rs.getBigDecimal("destination_location_longitude"));
            boolean routing=validLocation(origin) && validLocation(destination);
            UUID rating=rs.getObject("rating_id",UUID.class);
            Facts facts=new Facts(statuses,rs.getObject("requested_pickup_business_date",LocalDate.class),rating,rs.getBoolean("context_matches"),
                    rs.getBoolean("load_pre_dispatch"),rs.getBoolean("trip_pre_dispatch"),routing,rs.getBoolean("assignment_conflict"));
            return new State(scope,facts,new RoutingContext(origin,destination,rs.getObject("pickup_stop_id",UUID.class),
                    instant(rs.getObject("appointment_start",OffsetDateTime.class)),instant(rs.getObject("appointment_end",OffsetDateTime.class))),
                    rating==null?null:new Revenue(rating,scope.loadId(),rs.getString("currency"),rs.getBigDecimal("subtotal")),rs.getBoolean("is_hazmat"),
                    rs.getObject("assigned_truck_id",UUID.class),rs.getString("active_assignments"),rs.getString("other_truck_trips"));
        },json.writeValueAsString(candidates),start.atOffset(ZoneOffset.UTC),end.atOffset(ZoneOffset.UTC));
        var result=new HashMap<Scope,State>();rows.forEach(s->result.put(s.scope(),s));return Map.copyOf(result);
    }
    private static boolean validLocation(Location p) {
        return p.latitude()!=null && p.longitude()!=null && p.latitude().abs().compareTo(java.math.BigDecimal.valueOf(90))<=0
                && p.longitude().abs().compareTo(java.math.BigDecimal.valueOf(180))<=0;
    }
    private static Instant instant(OffsetDateTime value){return value==null?null:value.toInstant();}
}
