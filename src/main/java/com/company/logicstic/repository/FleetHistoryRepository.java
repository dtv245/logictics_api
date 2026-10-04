package com.company.logicstic.repository;

import com.company.logicstic.service.fleet.FleetHistory.*;
import com.company.logicstic.service.fleet.FleetHistory.Period;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @RequiredArgsConstructor
public class FleetHistoryRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public void lock(String identity){jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"FLEET:"+identity);}
    public boolean trucksExist(List<UUID> ids){return jdbc.queryForObject("select count(*) from trucks where id in (select value::uuid from jsonb_array_elements_text(?::jsonb))",Long.class,json.writeValueAsString(ids))==ids.size();}
    public Optional<Policy> policy(UUID id){return policies("id=?",id).stream().findFirst();}
    public Optional<Policy> policy(String code,int version){return policies("policy_code=? and policy_version=?",code,version).stream().findFirst();}
    private List<Policy> policies(String where,Object...args) {
        return jdbc.query("select * from fleet_policy_versions where "+where,(r,n)->{
            var m=json.readTree(r.getString("mappings"));
            return new Policy(r.getObject("id",UUID.class),r.getString("policy_code"),r.getInt("policy_version"),
                json.convertValue(m.get("membershipStates"),json.getTypeFactory().constructMapType(Map.class,String.class,Boolean.class)),
                json.convertValue(m.get("capacityStates"),json.getTypeFactory().constructMapType(Map.class,String.class,Boolean.class)),
                json.convertValue(m.get("activityStates"),json.getTypeFactory().constructMapType(Map.class,String.class,Activity.class)),
                json.readValue(r.getString("sources"),json.getTypeFactory().constructCollectionType(List.class,Source.class)),r.getString("approval_reference"),r.getObject("published_by",UUID.class),instant(r,"published_at"));
        },args);
    }
    public void insert(Policy p){jdbc.update("insert into fleet_policy_versions values (?,?,?,?::jsonb,?::jsonb,?,?,?)",p.id(),p.code(),p.version(),
        json.writeValueAsString(Map.of("membershipStates",p.membershipStates(),"capacityStates",p.capacityStates(),"activityStates",p.activityStates())),json.writeValueAsString(p.sources()),p.approvalReference(),p.publishedBy(),at(p.publishedAt()));}
    public Optional<Event> event(UUID id){return events("id=?",id).stream().findFirst();}
    public Optional<Event> event(Source s,String id){return events("source_type=? and source_reference=? and source_version=? and source_event_id=?",s.type(),s.reference(),s.version(),id).stream().findFirst();}
    private List<Event> events(String where,Object...args){return jdbc.query("select * from vehicle_status_events where "+where,(r,n)->new Event(r.getObject("id",UUID.class),r.getObject("policy_id",UUID.class),r.getObject("truck_id",UUID.class),Kind.valueOf(r.getString("kind")),r.getString("status"),r.getString("classification"),instant(r,"occurred_at"),instant(r,"valid_until"),source(r),r.getString("source_event_id"),r.getObject("trip_id",UUID.class),r.getObject("load_id",UUID.class),r.getString("execution_reference"),r.getObject("supersedes_event_id",UUID.class),r.getString("reason_code"),r.getString("reason"),r.getObject("captured_by",UUID.class),instant(r,"captured_at"),r.getString("normalized_input_hash")),args);}
    public boolean supersededEvent(UUID id){return jdbc.queryForObject("select exists(select 1 from vehicle_status_events where supersedes_event_id=?)",Boolean.class,id);}
    public boolean executionContext(UUID trip,UUID load){return jdbc.queryForObject("""
        select (?::uuid is null or exists(select 1 from trips where id=?::uuid))
           and (?::uuid is null or exists(select 1 from loads where id=?::uuid))
           and (?::uuid is null or ?::uuid is null or exists(select 1 from trip_stops where trip_id=?::uuid and load_id=?::uuid))
        """,Boolean.class,trip,trip,load,load,trip,load,trip,load);}
    public void insert(Event e){jdbc.update("""
        insert into vehicle_status_events(id,policy_id,truck_id,kind,status,classification,occurred_at,valid_until,source_type,source_reference,source_version,source_event_id,
          trip_id,load_id,execution_reference,supersedes_event_id,reason_code,reason,captured_by,captured_at,normalized_input_hash) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,e.id(),e.policyId(),e.truckId(),e.kind().name(),e.status(),e.classification(),at(e.occurredAt()),at(e.validUntil()),e.source().type(),e.source().reference(),e.source().version(),e.sourceEventId(),e.tripId(),e.loadId(),e.executionReference(),e.supersedesEventId(),e.reasonCode(),e.reason(),e.capturedBy(),at(e.capturedAt()),e.normalizedInputHash());}
    public Optional<Mileage> mileage(UUID id){return mileages("id=?",id).stream().findFirst();}
    public Optional<Mileage> mileage(Source s,String id){return mileages("source_type=? and source_reference=? and source_version=? and source_event_id=?",s.type(),s.reference(),s.version(),id).stream().findFirst();}
    private List<Mileage> mileages(String where,Object...args){return jdbc.query("select * from fleet_mileage_attributions where "+where,(r,n)->new Mileage(r.getObject("id",UUID.class),r.getObject("policy_id",UUID.class),r.getObject("truck_id",UUID.class),r.getObject("trip_id",UUID.class),instant(r,"completed_at"),r.getBigDecimal("loaded_miles"),r.getBigDecimal("empty_miles"),r.getBigDecimal("actual_miles"),source(r),r.getString("source_event_id"),r.getObject("supersedes_id",UUID.class),r.getString("reason_code"),r.getString("reason"),r.getObject("captured_by",UUID.class),instant(r,"captured_at"),r.getString("normalized_input_hash")),args);}
    public boolean supersededMileage(UUID id){return jdbc.queryForObject("select exists(select 1 from fleet_mileage_attributions where supersedes_id=?)",Boolean.class,id);}
    public boolean originalMileageExists(UUID trip){return jdbc.queryForObject("select exists(select 1 from fleet_mileage_attributions where trip_id=? and supersedes_id is null)",Boolean.class,trip);}
    public record ActualTrip(Instant completedAt,boolean cancelled,BigDecimal loaded,BigDecimal empty,BigDecimal actual) {}
    public Optional<ActualTrip> actualTrip(UUID id){return jdbc.query("select completed_at,cancelled_at,loaded_miles,empty_miles,actual_distance_miles from trips where id=? for no key update",(r,n)->new ActualTrip(r.getObject("completed_at",OffsetDateTime.class)==null?null:instant(r,"completed_at"),r.getObject("cancelled_at")!=null,r.getBigDecimal("loaded_miles"),r.getBigDecimal("empty_miles"),r.getBigDecimal("actual_distance_miles")),id).stream().findFirst();}
    public void insert(Mileage m){jdbc.update("""
        insert into fleet_mileage_attributions(id,policy_id,truck_id,trip_id,completed_at,loaded_miles,empty_miles,actual_miles,source_type,source_reference,source_version,source_event_id,supersedes_id,reason_code,reason,captured_by,captured_at,normalized_input_hash)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,m.id(),m.policyId(),m.truckId(),m.tripId(),at(m.completedAt()),m.loadedMiles(),m.emptyMiles(),m.actualMiles(),m.source().type(),m.source().reference(),m.source().version(),m.sourceEventId(),m.supersedesId(),m.reasonCode(),m.reason(),m.capturedBy(),at(m.capturedAt()),m.normalizedInputHash());}

    /** Sweep the union of actual evidence boundaries in SQL; no Java full-event-history materialization. */
    public List<Durations> durations(UUID policy,List<UUID> trucks,Period period) {
        return jdbc.query("""
        with params as (select ?::uuid policy,?::timestamptz lo,?::timestamptz hi), scope as
          (select value::uuid truck from jsonb_array_elements_text(?::jsonb)),
        active as (select e.* from vehicle_status_events e join scope s on s.truck=e.truck_id cross join params p
          where e.policy_id=p.policy and e.occurred_at<p.hi and e.valid_until>p.lo
          and not exists(select 1 from vehicle_status_events child where child.supersedes_event_id=e.id)),
        boundaries as (select s.truck,p.lo t from scope s cross join params p union select s.truck,p.hi from scope s cross join params p
          union select truck_id,greatest(occurred_at,p.lo) from active cross join params p
          union select truck_id,least(valid_until,p.hi) from active cross join params p),
        spans as (select truck,t,lead(t) over(partition by truck order by t) until from boundaries),
        classified as (select s.*,extract(epoch from (until-t)) seconds,h.* from spans s
          cross join lateral (select count(distinct status) filter(where kind='MEMBERSHIP') mc,min(classification) filter(where kind='MEMBERSHIP') m,
            count(distinct status) filter(where kind='CAPACITY') cc,min(classification) filter(where kind='CAPACITY') c,
            count(distinct status) filter(where kind='ACTIVITY') ac,min(classification) filter(where kind='ACTIVITY') a,
            bool_or(classification='PRODUCTIVE' and kind='ACTIVITY') productive
            from active e where e.truck_id=s.truck and e.occurred_at<=s.t and e.valid_until>=s.until) h where until>t),
        flags as (select *, (mc>1 or cc>1 or ac>1 or (coalesce(productive,false) and (m is distinct from 'IN' or c is distinct from 'AVAILABLE'))) conflict,
          (mc=0 or m='UNAVAILABLE' or (m='IN' and (cc=0 or c='UNAVAILABLE' or (c='AVAILABLE' and (ac=0 or a='UNAVAILABLE'))))) gap
          from classified)
        select truck,sum(seconds) scope_seconds,coalesce(sum(seconds) filter(where not conflict and mc=1 and m='IN'),0) member_seconds,
          coalesce(sum(seconds) filter(where not conflict and not gap and m='IN' and c='AVAILABLE' and a<>'EXCLUDED'),0) capacity_seconds,
          coalesce(sum(seconds) filter(where not conflict and not gap and m='IN' and c='AVAILABLE' and a='PRODUCTIVE'),0) productive_seconds,
          coalesce(sum(seconds) filter(where gap),0) gap_seconds,coalesce(sum(seconds) filter(where conflict),0) conflict_seconds,
          (select count(*) from active e where e.truck_id=f.truck) event_count from flags f group by truck order by truck
        """,(r,n)->new Durations(r.getObject("truck",UUID.class),r.getBigDecimal("scope_seconds"),r.getBigDecimal("member_seconds"),r.getBigDecimal("capacity_seconds"),r.getBigDecimal("productive_seconds"),r.getBigDecimal("gap_seconds"),r.getBigDecimal("conflict_seconds"),r.getLong("event_count")),policy,at(period.from()),at(period.to()),json.writeValueAsString(trucks));
    }
    public record MileageTotals(BigDecimal loaded,BigDecimal empty,BigDecimal actual,long count,long missing) {}
    public MileageTotals mileageTotals(UUID policy,List<UUID> trucks,Period period) {
        return jdbc.queryForObject("""
        with active as (select m.* from fleet_mileage_attributions m where not exists(select 1 from fleet_mileage_attributions c where c.supersedes_id=m.id)),
        valid as (select * from active where policy_id=? and truck_id in(select value::uuid from jsonb_array_elements_text(?::jsonb)) and completed_at>=? and completed_at<?)
        select sum(loaded_miles) loaded,sum(empty_miles) empty,sum(actual_miles) actual,count(*) count,
          (select count(*) from trips t where t.completed_at>=? and t.completed_at<? and t.cancelled_at is null
             and not exists(select 1 from active m where m.trip_id=t.id)) missing from valid
        """,(r,n)->new MileageTotals(r.getBigDecimal("loaded"),r.getBigDecimal("empty"),r.getBigDecimal("actual"),r.getLong("count"),r.getLong("missing")),policy,json.writeValueAsString(trucks),at(period.from()),at(period.to()),at(period.from()),at(period.to()));
    }
    private static Source source(java.sql.ResultSet r)throws java.sql.SQLException{return new Source(r.getString("source_type"),r.getString("source_reference"),r.getString("source_version"));}
    private static Instant instant(java.sql.ResultSet r,String column)throws java.sql.SQLException{return r.getObject(column,OffsetDateTime.class).toInstant();}
    private static OffsetDateTime at(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
