package com.company.logicstic.service.payroll;

import com.company.logicstic.common.*;
import com.company.logicstic.common.enums.InvoiceStatus;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.calculation.CalculationSnapshotService;
import com.company.logicstic.service.rating.RatingFingerprintService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service @RequiredArgsConstructor @Slf4j
public class SettlementRevenueService {
    private final SettlementRevenueGuard guard;
    private final DriverSettlementRepository settlements;
    private final SettlementLineRepository lines;
    private final InvoiceRepository invoices;
    private final EmployeeRepository employees;
    private final DriverPayEngine engine;
    private final FinancialRoundingPolicy rounding;
    private final CalculationSnapshotService snapshots;
    private final RatingFingerprintService fingerprints;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final ObjectMapper json;

    public record Recalculate(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=120) String idempotencyKey,@jakarta.validation.constraints.NotNull UUID expectedSnapshotId,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=80) String reasonCode,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=300) String reason) { }
    public record Adjustment(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=120) String idempotencyKey,@jakarta.validation.constraints.NotNull UUID affectedDocumentId,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=80) String reasonCode,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=300) String reason) { }
    public record Impact(UUID originalSettlementId,UUID affectedDocumentId,UUID adjustmentSettlementId,
            BigDecimal economicDelta,BigDecimal payDelta,String outcome,UUID commandId) { }

    private void validate(String key,String code,String reason,UUID actor) {
        if(key==null || key.isBlank() || key.length()>120 || code==null || code.isBlank() || code.length()>80 || reason==null || reason.isBlank() || reason.length()>300)
            throw new BadRequestException("SETTLEMENT_REVENUE_INPUT_REQUIRED","Explicit key/reasonCode/reason required");
        if(actor==null || !employees.existsById(actor))throw new ForbiddenException("Persisted tenant Accounting actor required");
    }
    private <T> T replay(String operation,String key,String hash,Class<T> type) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","settlement-revenue:"+operation+":"+key);
        var prior=jdbc.queryForList("select input_hash,outcome::text from settlement_revenue_commands where operation=? and request_key=?",operation,key);
        if(prior.isEmpty())return null;
        if(!hash.equals(prior.getFirst().get("input_hash")))throw new ApiException(HttpStatus.CONFLICT,"SETTLEMENT_REVENUE_IDEMPOTENCY_CONFLICT","Key has different normalized financial input");
        return json.readValue((String)prior.getFirst().get("outcome"),type);
    }
    private void command(UUID id,String operation,String key,String hash,Object input,Object outcome,UUID settlement,UUID actor,long started) {
        jdbc.update("insert into settlement_revenue_commands(id,operation,request_key,input_hash,input,outcome,settlement_id,actor,captured_at) values (?,?,?,?,?::jsonb,?::jsonb,?,?,?)",
                id,operation,key,hash,json.writeValueAsString(input),json.writeValueAsString(outcome),settlement,actor,OffsetDateTime.now(ZoneOffset.UTC));
        String version=outcome instanceof Impact impact
                ? jdbc.queryForObject("select policy_version::text from settlement_billing_adjustments where original_settlement_id=? and affected_document_id=?",String.class,settlement,impact.affectedDocumentId())
                : jdbc.queryForObject("select pay_policy_version::text from settlements where id=?",String.class,settlement);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() {
                log.info("settlement_revenue outcome=SUCCESS operation={} settlementId={} policyVersion={} correlationId={} durationMs={}",operation,settlement,version,id,(System.nanoTime()-started)/1_000_000);
            }
        });
    }
    @Transactional
    public DriverSettlementView recalculate(UUID id,Recalculate r,UUID actor) {
        long started=System.nanoTime();
        if(r==null || r.expectedSnapshotId()==null)throw new BadRequestException("SETTLEMENT_REVENUE_INPUT_REQUIRED","Explicit expected snapshot required");
        validate(r.idempotencyKey(),r.reasonCode(),r.reason(),actor);var input=Map.of("settlementId",id,"request",r);String hash=fingerprints.hash(input);
        var prior=replay("REVENUE_RECALCULATE",r.idempotencyKey(),hash,DriverSettlementView.class);if(prior!=null)return prior;
        guard.lockSources(id);var s=settlements.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Settlement not found"));
        if(!"ORIGINAL".equals(s.getSettlementType()) || !Set.of("CALCULATED","VALIDATION_REQUIRED","IN_REVIEW","APPROVED").contains(s.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT,"SETTLEMENT_HISTORY_IMMUTABLE","Only nonfinal original settlement may explicitly recalculate revenue");
        if(!r.expectedSnapshotId().equals(s.getCalculationSnapshot().getId()))throw new ApiException(HttpStatus.CONFLICT,"SETTLEMENT_REVENUE_BASIS_STALE","Expected calculation snapshot changed");
        var sourceLines=lines.findBySettlementIdOrderById(id);var frozen=guard.frozen(s);
        if(frozen.isEmpty())throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","No evidenced percentage calculation to recalculate");
        List<PercentagePayCalculator.Result> current=new ArrayList<>();BigDecimal percentage=BigDecimal.ZERO;
        for(var old:frozen) {
            var line=guard.line(old,sourceLines);var next=guard.current(old,line);current.add(next);percentage=percentage.add(next.amount());
            line.setAmount(next.amount());line.setQuantity(next.eligibleRevenue());line.setRate(next.ratio());line.setSourceId(next.invoiceId());
        }
        BigDecimal gross=s.getGrossEarnings().subtract(s.getPercentagePay()).add(percentage),net=gross.subtract(s.getDeductionAmount()).add(s.getReimbursementAmount());
        ObjectNode calculation=(ObjectNode)json.readTree(s.getCalculationSnapshot().getInputJson());
        calculation.set("percentageCalculations",json.valueToTree(current));
        calculation.set("recalculationAudit",json.valueToTree(Map.of("previousSnapshotId",r.expectedSnapshotId(),"request",r,"actor",actor,"capturedAt",OffsetDateTime.now(ZoneOffset.UTC))));
        calculation.set("lines",json.valueToTree(sourceLines.stream().map(l->Map.of("type",l.getLineType(),"class",l.getLineClass(),"amount",l.getAmount(),"currency",l.getCurrency(),
                "sourceType",Objects.toString(l.getSourceType(),""),"sourceId",Objects.toString(l.getSourceId(),""),"loadId",l.getLoad()==null?"":l.getLoad().getId().toString(),
                "tripId",l.getTrip()==null?"":l.getTrip().getId().toString(),"businessDate",Objects.toString(l.getBusinessDate(),""))).toList()));
        var result=(ObjectNode)json.readTree(s.getCalculationSnapshot().getResultJson());result.put("percentagePay",percentage);result.put("grossEarnings",gross);result.put("settlementNet",net);
        var snapshot=snapshots.recordSnapshot("DRIVER_SETTLEMENT",id,"DRIVER_PAY","DriverPayEngine","3","DRIVER_PAY_POLICY",s.getPayPolicy().getId(),s.getPayPolicyVersion().toString(),
                json.writeValueAsString(calculation),json.writeValueAsString(result),s.getCurrency(),actor,"revenue-recalculation");
        s.setPercentagePay(percentage);s.setGrossEarnings(gross);s.setSettlementNet(net);s.setCalculationSnapshot(snapshot);s.setCalculatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        s.setStatus("CALCULATED");s.setValidationReason(null);s.setReviewedAt(null);s.setReviewedBy(null);s.setApprovedAt(null);s.setApprovedBy(null);
        sourceLines.forEach(l->l.setCalculationSnapshot(snapshot));em.flush();var outcome=engine.get(id);
        command(UUID.randomUUID(),"REVENUE_RECALCULATE",r.idempotencyKey(),hash,input,outcome,id,actor,started);return outcome;
    }
    @Transactional
    public Impact adjust(UUID id,Adjustment r,UUID actor) {
        long started=System.nanoTime();
        if(r==null || r.affectedDocumentId()==null)throw new BadRequestException("SETTLEMENT_REVENUE_INPUT_REQUIRED","Explicit affected document required");
        validate(r.idempotencyKey(),r.reasonCode(),r.reason(),actor);var input=Map.of("settlementId",id,"request",r);String hash=fingerprints.hash(input);
        var prior=replay("REVENUE_ADJUSTMENT",r.idempotencyKey(),hash,Impact.class);if(prior!=null)return prior;
        guard.lockSources(id);var s=settlements.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Settlement not found"));
        if(!"ORIGINAL".equals(s.getSettlementType()) || !Set.of("LOCKED","PAYMENT_SCHEDULED","PAID").contains(s.getStatus()))
            throw new BadRequestException("SETTLEMENT_NOT_FINALIZED","Revenue adjustment requires finalized original");
        var existing=jdbc.queryForList("select command_id from settlement_billing_adjustments where original_settlement_id=? and affected_document_id=?",id,r.affectedDocumentId());
        if(!existing.isEmpty()) {
            var outcome=jdbc.queryForObject("select outcome::text from settlement_revenue_commands where id=?",String.class,existing.getFirst().get("command_id"));
            var result=json.readValue(outcome,Impact.class);
            command(UUID.randomUUID(),"REVENUE_ADJUSTMENT",r.idempotencyKey(),hash,input,result,id,actor,started);
            return result; // Bind the retry key as well as deduplicating the business source.
        }
        var d=invoices.findById(r.affectedDocumentId()).orElseThrow(()->new ResourceNotFoundException("Billing document not found"));
        if(d.getInvoicePurpose()==null || !InvoiceStatus.fromString(d.getStatus()).countsAsRevenue())throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Explicit eligible issued document required");
        CurrencyGuard.requireSameCurrency(s.getCurrency(),d.getSubtotalCurrency());
        var sourceLines=lines.findBySettlementIdOrderById(id);var matching=guard.frozen(s).stream().filter(x->guard.line(x,sourceLines).getLoad().getId().equals(d.getLoad().getId())).toList();
        if(matching.size()!=1)throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Document must match exactly one frozen attributed revenue calculation");
        var old=matching.getFirst();var originalLine=guard.line(old,sourceLines);guard.policy(old,originalLine);
        var originalInvoice=invoices.findById(old.invoiceId()).orElseThrow(()->new ResourceNotFoundException("Original revenue document missing"));
        if(!Objects.equals(originalInvoice.getBillingChainId(),d.getBillingChainId()) || !Objects.equals(originalInvoice.getCustomer().getId(),d.getCustomer().getId()))
            throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Affected document must belong to original explicit billing chain/customer");
        boolean included=old.documents()!=null ? old.documents().stream().anyMatch(x->x.invoiceId().equals(d.getId())) : old.invoiceId().equals(d.getId());
        BigDecimal delta=!included && "NET_ELIGIBLE_REVENUE".equals(old.revenueBasis()) ? d.getSubtotalAmount().multiply(BigDecimal.valueOf(d.economicSign())) : BigDecimal.ZERO;
        BigDecimal priorEconomic=jdbc.queryForObject("select coalesce(sum(economic_delta),0) from settlement_billing_adjustments where original_settlement_id=? and original_line_id=?",BigDecimal.class,id,originalLine.getId());
        BigDecimal priorPay=jdbc.queryForObject("select coalesce(sum(pay_delta),0) from settlement_billing_adjustments where original_settlement_id=? and original_line_id=?",BigDecimal.class,id,originalLine.getId());
        BigDecimal previous=old.eligibleRevenue().add(priorEconomic),result=previous.add(delta);
        if(result.signum()<0)throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Correction cannot produce negative eligible revenue");
        if(!rounding.version().equals(old.roundingPolicyVersion()))throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Frozen pay rounding policy implementation required");
        BigDecimal pay=rounding.money(result.multiply(old.ratio()),s.getCurrency(),FinancialRoundingPolicy.Boundary.ALLOCATION).subtract(old.amount()).subtract(priorPay);
        UUID child=null;if(pay.signum()!=0) {
            var line=new SettlementAdjustmentRequest.Line(pay.signum()>0?"EARNING":"DEDUCTION",pay.signum()>0?"REVENUE_PERCENT_ADJUSTMENT":"DRIVER_COST_CORRECTION",
                    r.reason(),pay.abs(),originalLine.getLoad().getId(),originalLine.getTrip()==null?null:originalLine.getTrip().getId());
            child=engine.createAdjustment(id,new SettlementAdjustmentRequest("billing-"+d.getId(),r.reason(),List.of(line))).id();
        }
        em.flush();UUID command=UUID.randomUUID();var outcome=new Impact(id,d.getId(),child,delta,pay,child==null?"NO_PAY_ADJUSTMENT_REQUIRED":"ADJUSTMENT_CREATED",command);
        jdbc.update("""
            insert into settlement_billing_adjustments(original_settlement_id,affected_document_id,original_line_id,adjustment_settlement_id,command_id,
            policy_id,policy_version,earning_date,revenue_basis,economic_delta,pay_delta,previous_revenue,resulting_revenue,reason_code,reason,actor,captured_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,d.getId(),originalLine.getId(),child,command,old.policyId(),old.policyVersion(),originalLine.getBusinessDate(),old.revenueBasis(),delta,pay,previous,result,r.reasonCode(),r.reason(),actor,OffsetDateTime.now(ZoneOffset.UTC));
        command(command,"REVENUE_ADJUSTMENT",r.idempotencyKey(),hash,input,outcome,id,actor,started);return outcome;
    }
    /** Called inside the short issue transaction: all side effects are DB-owned, no provider call. */
    public void lockIssueSources(UUID loadId) {
        // Lock the union in deterministic order before taking any invoice/settlement row lock.
        var sourceLoads=jdbc.queryForList("""
            select load_id from settlement_lines where line_type='REVENUE_PERCENT' and load_id is not null and settlement_id in
            (select settlement_id from settlement_lines where line_type='REVENUE_PERCENT' and load_id=?)
            union select ?::uuid order by load_id
            """,UUID.class,loadId,loadId);
        for(UUID source:sourceLoads)jdbc.queryForList("select id from loads where id=? for update",UUID.class,source);
    }
    public void issued(UUID documentId,UUID loadId,UUID actor) {
        var parents=jdbc.queryForList("""
            select distinct s.id from settlements s join settlement_lines l on l.settlement_id=s.id
            join invoices original on original.id=l.source_id join invoices affected on affected.id=?
            where s.settlement_type='ORIGINAL' and s.status in ('LOCKED','PAYMENT_SCHEDULED','PAID')
            and l.line_type='REVENUE_PERCENT' and l.load_id=? and original.billing_chain_id=affected.billing_chain_id order by s.id
            """,UUID.class,documentId,loadId);
        for(UUID parent:parents)adjust(parent,new Adjustment("issued-"+parent+"-"+documentId,documentId,"BILLING_DOCUMENT_ISSUED","Issued billing document "+documentId),actor);
    }
}
