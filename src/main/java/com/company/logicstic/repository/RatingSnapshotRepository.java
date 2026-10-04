package com.company.logicstic.repository;

import com.company.logicstic.service.rating.domain.*;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @RequiredArgsConstructor
public class RatingSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public Optional<AcceptedRatingSnapshot> find(UUID id){return rows("id=?",id);}
    public Optional<AcceptedRatingSnapshot> request(String key){return rows("operation='RATING_ACCEPT' and idempotency_key=?",key);}
    private Optional<AcceptedRatingSnapshot> rows(String predicate,Object arg){
        return jdbc.query("select * from accepted_rating_snapshots where "+predicate,(rs,n)->new AcceptedRatingSnapshot(rs.getObject("id",UUID.class),
                json.readValue(rs.getString("calculation"),RatingPreview.class),rs.getObject("accepted_by",UUID.class),rs.getObject("accepted_at",OffsetDateTime.class),
                rs.getObject("supersedes_snapshot_id",UUID.class),rs.getString("reason_code"),rs.getString("reason"),rs.getString("idempotency_key"),rs.getString("command_hash")),arg).stream().findFirst();
    }
    public void lockRequest(String key){jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"RATING_ACCEPT:"+key);}
    public void freezeInputs(UUID load, java.util.List<UUID> charges){
        jdbc.query("select id from loads where id=? for update",rs->{},load);
        for(UUID id:charges.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList())
            jdbc.query("select id from accessorial_charges where id=? for update",rs->{},id);
        // Shared lock permits concurrent acceptances, but prevents a new rule publication until commit.
        jdbc.execute("lock table customer_rate_rule_versions in share mode");
    }
    public void insert(AcceptedRatingSnapshot s){
        var p=s.calculation();var i=p.inputs();
        jdbc.update("""
                insert into accepted_rating_snapshots(id,load_id,customer_id,currency,pricing_date,pricing_date_source,pricing_date_change_id,
                rule_id,rule_version,rounding_policy_code,rounding_policy_version,subtotal,calculation,input_hash,result_hash,accepted_by,accepted_at,
                supersedes_snapshot_id,reason_code,reason,operation,idempotency_key,command_hash)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,'RATING_ACCEPT',?,?)
                """,s.snapshotId(),i.loadId(),i.matchContext().customerId(),p.currency(),i.pricingDate().pricingDate(),i.pricingDate().pricingDateSource(),i.pricingDate().sourceChangeId(),
                i.rule().ruleId(),i.rule().version(),p.roundingPolicyCode(),p.roundingPolicyVersion(),p.subtotal(),json.writeValueAsString(p),p.inputHash(),p.resultHash(),
                s.acceptedBy(),s.acceptedAt(),s.supersedesSnapshotId(),s.reasonCode(),s.reason(),s.idempotencyKey(),s.commandHash());
    }
}
