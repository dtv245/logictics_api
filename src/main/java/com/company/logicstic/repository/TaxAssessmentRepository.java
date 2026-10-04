package com.company.logicstic.repository;

import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import com.company.logicstic.service.billing.domain.TaxAssessment;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class TaxAssessmentRepository {
    private final JdbcTemplate jdbc;
    public void lock(UUID id) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, "TAX_ASSESSMENT:" + id);
    }
    public Optional<TaxAssessment> find(UUID id) {
        return jdbc.query("select * from invoice_tax_assessments where id=?", (rs,n) ->
            new TaxAssessment(new TaxAssessmentRequest(rs.getObject("id",UUID.class),
                rs.getObject("load_id",UUID.class),rs.getObject("customer_id",UUID.class),
                rs.getString("source_type"),rs.getString("source_reference"),rs.getString("jurisdiction"),
                rs.getBigDecimal("taxable_basis"),rs.getBigDecimal("tax_amount"),rs.getString("currency"),
                rs.getString("policy_version"),rs.getObject("assessed_at",OffsetDateTime.class),
                rs.getString("assessed_by")),rs.getObject("captured_by",UUID.class),
                rs.getObject("captured_at",OffsetDateTime.class),rs.getString("input_hash")), id).stream().findFirst();
    }
    public void insert(TaxAssessment value) {
        var a=value.assessment();
        jdbc.update("""
            insert into invoice_tax_assessments(id,load_id,customer_id,source_type,source_reference,
            jurisdiction,taxable_basis,tax_amount,currency,policy_version,assessed_at,assessed_by,
            captured_by,captured_at,input_hash) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,a.assessmentId(),a.loadId(),a.customerId(),a.sourceType(),a.sourceReference(),a.jurisdiction(),
            a.taxableBasis(),a.taxAmount(),a.currency(),a.policyVersion(),a.assessedAt(),a.assessedBy(),
            value.capturedBy(),value.capturedAt(),value.inputHash());
    }
}
