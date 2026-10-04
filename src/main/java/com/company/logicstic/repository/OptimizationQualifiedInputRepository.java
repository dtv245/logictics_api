package com.company.logicstic.repository;

import com.company.logicstic.service.optimization.OptimizationForecastResolver.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @RequiredArgsConstructor
public class OptimizationQualifiedInputRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public boolean contextExists(Scope scope) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from trip_stops s,employees d,trucks t where s.load_id=? and s.trip_id=? and d.id=? and t.id=?)
                """, Boolean.class, scope.loadId(), scope.tripId(), scope.driverId(), scope.truckId()));
    }
    public Optional<Captured> find(UUID id) {
        return findAll(List.of(id)).stream().findFirst();
    }
    public List<Captured> findAll(Collection<UUID> ids) {
        if(ids.isEmpty()) return List.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return jdbc.query("select * from optimization_qualified_inputs where id in ("+placeholders+") order by id", (rs,n) -> new Captured(
                rs.getObject("id",UUID.class), rs.getObject("policy_id",UUID.class), Kind.valueOf(rs.getString("input_kind")),
                new Scope(rs.getObject("load_id",UUID.class),rs.getObject("trip_id",UUID.class),rs.getObject("driver_id",UUID.class),rs.getObject("truck_id",UUID.class)),
                new Source(rs.getString("source_type"),rs.getString("source_reference"),rs.getString("source_version"),SourceClass.valueOf(rs.getString("source_class"))),
                rs.getString("unit"),rs.getObject("observed_at",OffsetDateTime.class).toInstant(), instant(rs.getObject("expires_at",OffsetDateTime.class)),
                rs.getObject("max_age_seconds",Long.class),rs.getInt("evidence_version"),rs.getObject("supersedes_input_id",UUID.class),
                json.readValue(rs.getString("payload"),Payload.class),rs.getString("approval_reference"),rs.getString("reason_code"),rs.getString("reason"),
                rs.getObject("captured_by",UUID.class),rs.getObject("captured_at",OffsetDateTime.class).toInstant(),rs.getString("normalized_input_hash")),ids.toArray());
    }
    public boolean superseded(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from optimization_qualified_inputs where supersedes_input_id=?)",Boolean.class,id));
    }
    public Set<UUID> superseded(Collection<UUID> ids) {
        if(ids.isEmpty()) return Set.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return new HashSet<>(jdbc.query("select supersedes_input_id from optimization_qualified_inputs where supersedes_input_id in ("+placeholders+")",
                (rs,n)->rs.getObject(1,UUID.class),ids.toArray()));
    }
    public record Ledger(UUID id, UUID loadId, UUID tripId, UUID driverId, UUID truckId, long version, String category,
                         String basis, String status, String currency, BigDecimal amount, UUID approvedBy, Instant approvedAt) {}
    public List<Ledger> ledger(List<UUID> ids, boolean lock) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",",Collections.nCopies(ids.size(),"?"));
        return jdbc.query("select * from shipment_costs where id in ("+placeholders+") order by id"+(lock?" for update":""),
                (rs,n) -> new Ledger(rs.getObject("id",UUID.class),rs.getObject("load_id",UUID.class),rs.getObject("trip_id",UUID.class),
                        rs.getObject("driver_id",UUID.class),rs.getObject("truck_id",UUID.class),rs.getLong("version"),rs.getString("category"),
                        rs.getString("cost_basis"),rs.getString("status"),rs.getString("currency"),rs.getBigDecimal("amount"),
                        rs.getObject("approved_by",UUID.class),instant(rs.getObject("approved_at",OffsetDateTime.class))),ids.toArray());
    }
    public void insert(Captured input) {
        jdbc.update("""
                insert into optimization_qualified_inputs(id,policy_id,input_kind,load_id,trip_id,driver_id,truck_id,
                source_type,source_reference,source_version,source_class,unit,observed_at,expires_at,max_age_seconds,evidence_version,
                supersedes_input_id,payload,approval_reference,reason_code,reason,captured_by,captured_at,normalized_input_hash)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)
                """,input.id(),input.policyId(),input.kind().name(),input.scope().loadId(),input.scope().tripId(),input.scope().driverId(),input.scope().truckId(),
                input.source().type(),input.source().reference(),input.source().version(),input.source().classification().name(),input.unit(),
                at(input.observedAt()),at(input.expiresAt()),input.maxAgeSeconds(),input.evidenceVersion(),input.supersedesInputId(),
                json.writeValueAsString(input.payload()),input.approvalReference(),input.reasonCode(),input.reason(),input.capturedBy(),at(input.capturedAt()),input.normalizedInputHash());
        if(input.kind()==Kind.FORECAST_COST) for(Cost cost:input.payload().forecast().costs()) jdbc.update("""
                insert into optimization_qualified_forecast_costs(qualified_input_id,cost_id,ledger_version,category,amount,currency)
                values (?,?,?,?,?,?)
                """,input.id(),cost.costId(),cost.ledgerVersion(),cost.category().name(),cost.amount(),cost.currency());
    }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
    private static OffsetDateTime at(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
}
