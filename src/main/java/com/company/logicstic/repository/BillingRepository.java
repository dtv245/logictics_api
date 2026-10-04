package com.company.logicstic.repository;

import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.service.billing.domain.BillingInvoice;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository @RequiredArgsConstructor
public class BillingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record Prior(String hash,BillingInvoice outcome) { }
    public void lockRequest(String operation,String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"BILLING:"+operation+":"+key);
    }
    public Optional<Prior> request(String operation,String key) {
        return jdbc.query("select normalized_input_hash,outcome from invoice_billing_commands where operation=? and idempotency_key=?",
                (rs,n)->new Prior(rs.getString(1),json.readValue(rs.getString(2),BillingInvoice.class)),operation,key).stream().findFirst();
    }
    public void lockLoad(UUID load) {
        if(jdbc.query("select id from loads where id=? for update",(rs,n)->rs.getObject(1,UUID.class),load).isEmpty())
            throw new ResourceNotFoundException("Billing Load not found");
    }
    public boolean loadHasInvoice(UUID load) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from invoices where load_id=?)",Boolean.class,load));
    }
    public boolean primaryExists(UUID load,UUID customer,String currency) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from invoices where load_id=? and
                (invoice_purpose is null or (invoice_purpose='PRIMARY' and customer_id=? and subtotal_currency=?)))
                """,Boolean.class,load,customer,currency));
    }
    public java.math.BigDecimal credited(UUID line,String column) {
        if(!java.util.Set.of("amount_amount","tax_amount","credited_quantity").contains(column)) throw new IllegalArgumentException("Unsupported internal credit sum column");
        return jdbc.queryForObject("select coalesce(sum("+column+"),0) from invoice_line_items where credited_line_id=?",java.math.BigDecimal.class,line);
    }
    public boolean rebilled(UUID original) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from invoices where parent_invoice_id=? and invoice_purpose='REBILL')",Boolean.class,original));
    }
    public void creditEvidence(UUID rebill,java.util.List<UUID> ids) {
        for(UUID id:ids) jdbc.update("insert into invoice_rebill_credit_evidence(rebill_invoice_id,credit_invoice_id) values (?,?)",rebill,id);
    }
    public void claimCharges(BillingInvoice outcome) {
        for(var line:outcome.lines().stream().filter(x->"ACCESSORIAL".equals(x.componentType())).sorted(java.util.Comparator.comparing(x->x.sourceId().toString())).toList()) {
            var claim=jdbc.query("select invoice_id from invoice_charge_claims where charge_id=?",(rs,n)->rs.getObject(1,UUID.class),line.sourceId());
            if(!claim.isEmpty()) {
                if("REBILL".equals(outcome.purpose()) && Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists(select 1 from invoice_line_items l join invoices owner on owner.id=?
                        where l.invoice_id=? and l.type='ACCESSORIAL' and l.rating_source_id=? and owner.billing_chain_id=?)
                        """,Boolean.class,claim.getFirst(),outcome.parentInvoiceId(),line.sourceId(),outcome.billingChainId()))) continue;
                throw new com.company.logicstic.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                        "INVOICE_CHARGE_ALREADY_BILLED","Approved charge event is already claimed; supplemental cannot duplicate it");
            }
            jdbc.update("insert into invoice_charge_claims(charge_id,invoice_id) values (?,?)",line.sourceId(),outcome.invoiceId());
        }
    }
    public BillingInvoice current(UUID id,boolean lock) {
        var rows=jdbc.query("select b.outcome from invoices i join invoice_billing_commands b on b.id=i.billing_command_id where i.id=?"+(lock?" for update of i":""),
                (rs,n)->json.readValue(rs.getString(1),BillingInvoice.class),id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Rated billing invoice not found");
        var value=rows.getFirst();String status=jdbc.queryForObject("select status from invoices where id=?",String.class,id);
        return new BillingInvoice(value.invoiceId(),value.loadId(),value.customerId(),value.currency(),value.purpose(),value.economicSign(),status,
                value.snapshotId(),value.parentInvoiceId(),value.billingChainId(),value.subtotal(),value.tax(),value.total(),value.lines(),value.commandId(),value.actor(),value.capturedAt());
    }
    public void insert(BillingInvoice i,com.company.logicstic.dto.invoice.GenerateInvoiceRequest request) {
        jdbc.update("""
            insert into invoices(id,type,status,tax_behavior,subtotal_amount,subtotal_currency,tax_total_amount,tax_total_currency,
            total_amount,total_currency,load_id,customer_id,invoice_purpose,economic_sign,rating_snapshot_id,parent_invoice_id,
            billing_chain_id,billing_command_id,tax_requirement,tax_decision,tax_assessment_id)
            values (?,'FREIGHT',?,'exclusive',
                ?,?,?,?,?,?,
                ?,?,?,?,?,?,
                ?,?,?,?::jsonb,?)
            """,i.invoiceId(),i.status(),i.subtotal(),i.currency(),i.tax(),i.currency(),i.total(),i.currency(),i.loadId(),i.customerId(),
            i.purpose(),i.economicSign(),i.snapshotId(),i.parentInvoiceId(),i.billingChainId(),i.commandId(),
            request.taxDecision().requirement(),json.writeValueAsString(request.taxDecision()),request.taxDecision().assessmentId());
        lines(i);
    }
    private void lines(BillingInvoice i) {
        int order=0;
        for(var line:i.lines()) jdbc.update("""
            insert into invoice_line_items(id,invoice_id,description,type,quantity,"order",tax_rate_percent,tax_amount,amount_amount,amount_currency,
            rating_source_id,credited_line_id,credited_quantity) values (?,?,?,?,1,?,NULL,?,?,?,?,?,?)
            """,line.lineId(),i.invoiceId(),line.description(),line.componentType(),order++,line.taxAmount(),line.amount(),i.currency(),line.sourceId(),line.creditedLineId(),line.creditedQuantity());
    }
    public void command(String operation,String key,String hash,Object input,BillingInvoice outcome) {
        jdbc.update("""
            insert into invoice_billing_commands(id,operation,idempotency_key,normalized_input_hash,invoice_id,input,outcome,actor,captured_at)
            values (?,?,?,?,?,?::jsonb,?::jsonb,?,?)
            """,outcome.commandId(),operation,key,hash,outcome.invoiceId(),json.writeValueAsString(input),json.writeValueAsString(outcome),outcome.actor(),outcome.capturedAt());
    }
    public void updateDraft(BillingInvoice i,com.company.logicstic.dto.invoice.GenerateInvoiceRequest request) {
        jdbc.update("delete from invoice_charge_claims where invoice_id=?",i.invoiceId());
        jdbc.update("delete from invoice_line_items where invoice_id=?",i.invoiceId());
        jdbc.update("update invoices set subtotal_amount=?,tax_total_amount=?,total_amount=?,rating_snapshot_id=?,billing_command_id=?,tax_requirement=?,tax_decision=?::jsonb,tax_assessment_id=? where id=?",
                i.subtotal(),i.tax(),i.total(),i.snapshotId(),i.commandId(),request.taxDecision().requirement(),
                json.writeValueAsString(request.taxDecision()),request.taxDecision().assessmentId(),i.invoiceId());
        lines(i);
    }
    public void issue(BillingInvoice i) {
        jdbc.update("update invoices set status='ISSUED',billing_command_id=? where id=?",i.commandId(),i.invoiceId());
    }
}
