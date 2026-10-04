package com.company.logicstic.repository;

import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.Policy;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @RequiredArgsConstructor
public class OptimizationAuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public void lockCommand(String operation, String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, operation + ":" + key);
    }
    public Optional<PublishedPolicy> policy(UUID id) { return policies("id=?", id).stream().findFirst(); }
    public Optional<PublishedPolicy> policy(String code, int version) { return policies("policy_code=? and policy_version=?", code, version).stream().findFirst(); }
    private List<PublishedPolicy> policies(String predicate, Object... params) {
        return jdbc.query("select * from optimization_policy_versions where " + predicate, (rs,n) -> new PublishedPolicy(rs.getObject("id", UUID.class),
                rs.getString("policy_code"), rs.getInt("policy_version"), json.readValue(rs.getString("eligibility_source_policy"), Policy.class),
                json.readValue(rs.getString("scoring_policy"), OptimizationScoringPolicy.class), rs.getString("approval_reference"),
                rs.getObject("published_by", UUID.class), rs.getObject("published_at", OffsetDateTime.class).toInstant()), params);
    }
    public void insert(PublishedPolicy policy) {
        jdbc.update("""
                insert into optimization_policy_versions(id,policy_code,policy_version,eligibility_source_policy,scoring_policy,approval_reference,published_by,published_at)
                values (?,?,?,?::jsonb,?::jsonb,?,?,?)
                """, policy.id(), policy.code(), policy.version(), policyJson(policy.eligibilitySourcePolicy()),
                json.writeValueAsString(policy.scoringPolicy()), policy.approvalReference(), policy.publishedBy(), at(policy.publishedAt()));
    }
    public Optional<Run> run(UUID id) { return runs("id=?", id).stream().findFirst(); }
    public Optional<Run> request(String key) { return runs("idempotency_key=?", key).stream().findFirst(); }
    private List<Run> runs(String predicate, Object... params) {
        return jdbc.query("select * from optimization_runs where " + predicate, (rs,n) -> new Run(rs.getObject("id", UUID.class), rs.getObject("policy_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getObject("planning_until", OffsetDateTime.class).toInstant(), rs.getObject("created_by", UUID.class),
                rs.getString("idempotency_key"), rs.getString("normalized_input_hash"), json.readValue(rs.getString("request_snapshot"), RunRequest.class),
                json.readValue(rs.getString("policy_snapshot"), PolicySnapshot.class), rs.getObject("calculated_at", OffsetDateTime.class).toInstant(), rs.getLong("duration_millis"), rs.getString("correlation_id")), params);
    }
    public List<Candidate> candidates(UUID runId) {
        return jdbc.query("select * from optimization_assignments where run_id=? order by feasible desc,rank,id", (rs,n) -> new Candidate(rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getObject("load_id", UUID.class), rs.getObject("trip_id", UUID.class), rs.getObject("driver_id", UUID.class),
                rs.getObject("truck_id", UUID.class), rs.getObject("rating_snapshot_id", UUID.class), rs.getBoolean("feasible"),
                json.readValue(rs.getString("rejection_codes"), json.getTypeFactory().constructCollectionType(List.class, String.class)), rs.getBigDecimal("final_score"),
                rs.getObject("rank", Integer.class), rs.getString("input_fingerprint"), json.readValue(rs.getString("explanation"), Explanation.class)), runId);
    }
    public void insert(Run run) {
        jdbc.update("""
                insert into optimization_runs(id,policy_id,created_at,planning_until,created_by,idempotency_key,normalized_input_hash,request_snapshot,policy_snapshot,calculated_at,duration_millis,correlation_id)
                values (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)
                """, run.id(), run.policyId(), at(run.createdAt()), at(run.planningUntil()), run.createdBy(), run.idempotencyKey(), run.normalizedInputHash(),
                json.writeValueAsString(run.requestSnapshot()), snapshotJson(run.policySnapshot()), at(run.calculatedAt()), run.durationMillis(), run.correlationId());
    }
    public void insert(Candidate candidate) {
        jdbc.update("""
                insert into optimization_assignments(id,run_id,load_id,trip_id,driver_id,truck_id,rating_snapshot_id,feasible,rejection_codes,final_score,rank,input_fingerprint,explanation)
                values (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?::jsonb)
                """, candidate.id(), candidate.runId(), candidate.loadId(), candidate.tripId(), candidate.driverId(), candidate.truckId(), candidate.ratingSnapshotId(),
                candidate.feasible(), json.writeValueAsString(candidate.rejectionCodes()), candidate.finalScore(), candidate.rank(), candidate.inputFingerprint(), json.writeValueAsString(candidate.explanation()));
    }
    private static OffsetDateTime at(java.time.Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private String policyJson(Policy policy) {
        var tree = (tools.jackson.databind.node.ObjectNode) json.valueToTree(policy);
        var statuses = json.createObjectNode();
        policy.statusAllowlists().forEach((kind, values) -> statuses.set(kind.name(), json.valueToTree(values.stream().sorted().toList())));
        var sources = json.createObjectNode();
        policy.qualifiedSources().forEach((kind, values) -> sources.set(kind.name(), json.valueToTree(values.stream().sorted(java.util.Comparator
                .comparing(com.company.logicstic.service.optimization.domain.OptimizationEvidence.Source::type)
                .thenComparing(com.company.logicstic.service.optimization.domain.OptimizationEvidence.Source::reference)
                .thenComparing(com.company.logicstic.service.optimization.domain.OptimizationEvidence.Source::version)
                .thenComparing(s -> s.classification().name())).toList())));
        tree.set("statusAllowlists", statuses); tree.set("qualifiedSources", sources);
        return json.writeValueAsString(tree);
    }
    private String snapshotJson(PolicySnapshot snapshot) {
        var tree = (tools.jackson.databind.node.ObjectNode) json.valueToTree(snapshot);
        tree.set("eligibilitySourcePolicy", json.readTree(policyJson(snapshot.eligibilitySourcePolicy())));
        return json.writeValueAsString(tree);
    }
}
