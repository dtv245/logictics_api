package com.company.logicstic.repository;

import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @RequiredArgsConstructor
public class OptimizationAcceptanceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record Accepted(UUID id,UUID runId,UUID candidateId,UUID loadId,UUID tripId,UUID driverId,UUID truckId,UUID driverAssignmentId,
                           String originalInputFingerprint,Explanation revalidationSnapshot,UUID acceptedBy,Instant acceptedAt) {}
    public record Command(String key,String normalizedInputHash,Accepted outcome) {}
    public Optional<Accepted> run(UUID id){return accepted("run_id=?",id);}
    public Optional<Accepted> load(UUID id){return accepted("load_id=?",id);}
    private Optional<Accepted> accepted(String predicate,UUID id) {
        return jdbc.query("select * from optimization_acceptances where "+predicate,(rs,n)->read(rs),id).stream().findFirst();
    }
    private Accepted read(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Accepted(rs.getObject("id",UUID.class),rs.getObject("run_id",UUID.class),rs.getObject("candidate_id",UUID.class),rs.getObject("load_id",UUID.class),
                rs.getObject("trip_id",UUID.class),rs.getObject("driver_id",UUID.class),rs.getObject("truck_id",UUID.class),rs.getObject("driver_assignment_id",UUID.class),
                rs.getString("original_input_fingerprint"),json.readValue(rs.getString("revalidation_snapshot"),Explanation.class),rs.getObject("accepted_by",UUID.class),rs.getObject("accepted_at",OffsetDateTime.class).toInstant());
    }
    public Optional<Command> command(String key) {
        return jdbc.query("""
                select a.*,c.idempotency_key,c.normalized_input_hash from optimization_accept_commands c join optimization_acceptances a on a.id=c.acceptance_id
                where c.operation='OPTIMIZATION_ACCEPT' and c.idempotency_key=?
                """,(rs,n)->new Command(rs.getString("idempotency_key"),rs.getString("normalized_input_hash"),read(rs)),key).stream().findFirst();
    }
    public void lockResources(Scope scope,Run run,Target target,SourceSelection selection) {
        jdbc.query("select id from loads where id=? for no key update",rs->{},scope.loadId());
        jdbc.query("select id from trips where id=? for no key update",rs->{},scope.tripId());
        jdbc.query("select id from employees where id=? for no key update",rs->{},scope.driverId());
        jdbc.query("select id from trucks where id=? for no key update",rs->{},scope.truckId());
        jdbc.query("select id from trip_stops where trip_id=? order by id for no key update",rs->{},scope.tripId());
        jdbc.query("""
                select id from trip_driver_assignments where (trip_id=? or driver_id=?) and effective_from<? and (effective_to is null or effective_to>?) order by id for no key update
                """,rs->{},scope.tripId(),scope.driverId(),at(run.planningUntil()),at(run.createdAt()));
        // Source correction locks ledger before parent evidence; use the same order to avoid inverted locks.
        jdbc.query("""
                select c.id from shipment_costs c join optimization_qualified_forecast_costs q on q.cost_id=c.id
                where q.qualified_input_id=? order by c.id for no key update of c
                """,rs->{},selection.forecastInputId());
        jdbc.query("select id from optimization_qualified_inputs where id in (?,?,?) order by id for share",rs->{},selection.capacityInputId(),selection.qualificationInputId(),selection.forecastInputId());
    }
    public List<UUID> matchingAssignments(Scope scope,Instant planningUntil,Instant acceptedAt) {
        return jdbc.query("""
                select id from trip_driver_assignments where trip_id=? and driver_id=? and effective_from<? and (effective_to is null or effective_to>?) order by id
                """,(rs,n)->rs.getObject(1,UUID.class),scope.tripId(),scope.driverId(),at(planningUntil),at(acceptedAt));
    }
    public UUID assign(Scope scope,Run run,UUID actor,Instant acceptedAt) {
        var matching=matchingAssignments(scope,run.planningUntil(),acceptedAt);
        if(matching.size()>1)throw new com.company.logicstic.exception.ConflictException("OPTIMIZATION_CANDIDATE_STALE","Matching assignment history is ambiguous; no implicit latest-row selection");
        jdbc.update("update trips set truck_id=?,version=version+1 where id=?",scope.truckId(),scope.tripId());
        if(!matching.isEmpty())return matching.getFirst();
        UUID id=UUID.randomUUID();
        // PRIMARY is the established TripExecutionService assignment convention, not a new optimizer pay rule.
        // Open actual assignment is closed only by the real unassign/execution workflow, never by forecast horizon.
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,assignment_type,assigned_at,effective_from,created_at,created_by)
                values (?,?,?,'PRIMARY',?,?,?,?)
                """,id,scope.tripId(),scope.driverId(),at(acceptedAt),at(acceptedAt),at(acceptedAt),actor.toString());
        return id;
    }
    public void insert(Accepted accepted) {
        jdbc.update("""
                insert into optimization_acceptances(id,run_id,candidate_id,load_id,trip_id,driver_id,truck_id,driver_assignment_id,original_input_fingerprint,revalidation_snapshot,accepted_by,accepted_at)
                values (?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
                """,accepted.id(),accepted.runId(),accepted.candidateId(),accepted.loadId(),accepted.tripId(),accepted.driverId(),accepted.truckId(),accepted.driverAssignmentId(),
                accepted.originalInputFingerprint(),json.writeValueAsString(accepted.revalidationSnapshot()),accepted.acceptedBy(),at(accepted.acceptedAt()));
    }
    public void recordCommand(String key,String hash,Accepted accepted,UUID actor,Instant at) {
        jdbc.update("""
                insert into optimization_accept_commands(operation,idempotency_key,normalized_input_hash,acceptance_id,recorded_by,recorded_at)
                values ('OPTIMIZATION_ACCEPT',?,?,?,?,?)
                """,key,hash,accepted.id(),actor,at(at));
    }
    private static OffsetDateTime at(Instant value){return value.atOffset(ZoneOffset.UTC);}
}
