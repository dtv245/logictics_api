package com.company.logicstic.service.billing;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.invoice.GenerateInvoiceRequest;
import com.company.logicstic.dto.invoice.BillingCorrectionRequests;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.billing.domain.BillingInvoice;
import com.company.logicstic.service.rating.*;
import com.company.logicstic.service.rating.domain.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Slf4j
public class BillingService {
    private final BillingRepository billing;
    private final RatingSnapshotRepository snapshots;
    private final TaxAssessmentRepository taxes;
    private final EmployeeRepository employees;
    private final RatingFingerprintService fingerprints;
    private final CurrencyScaleProvider scales;
    private final com.company.logicstic.service.payroll.SettlementRevenueService settlementRevenue;

    private ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
    private void key(String key) {
        if(key==null || key.isBlank() || key.length()>120) throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Explicit idempotencyKey required");
    }
    private void actor(UUID id) {
        if(id==null || !employees.existsById(id)) throw new ForbiddenException("Billing actor must map to persisted tenant employee");
    }
    private BillingInvoice replay(String operation,String key,String hash) {
        billing.lockRequest(operation,key);var prior=billing.request(operation,key);
        if(prior.isEmpty()) return null;
        if(!hash.equals(prior.get().hash())) throw conflict("INVOICE_IDEMPOTENCY_CONFLICT","Idempotency key already has different normalized inputs");
        return prior.get().outcome();
    }
    private GenerateInvoiceRequest normalize(GenerateInvoiceRequest request) {
        if(request==null || request.snapshotId()==null || request.lineTaxes()==null || request.lineTaxes().stream().anyMatch(Objects::isNull))
            throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Accepted snapshot and explicit tax line selection required");
        key(request.idempotencyKey());
        return new GenerateInvoiceRequest(request.idempotencyKey(),request.snapshotId(),request.taxDecision(),request.lineTaxes().stream()
                .sorted(Comparator.comparing(x->Objects.toString(x.componentType(),"")+":"+Objects.toString(x.sourceId(),""))).toList());
    }
    private AcceptedRatingSnapshot accepted(UUID id) {
        return snapshots.find(id).orElseThrow(()->new ResourceNotFoundException("Accepted rating snapshot not found"));
    }
    private Map<String,BigDecimal> assess(GenerateInvoiceRequest request,AcceptedRatingSnapshot snapshot,List<RatingLine> lines) {
        var decision=request.taxDecision();
        if(decision==null || decision.requirement()==null || !Set.of("REQUIRED","NOT_REQUIRED").contains(decision.requirement())
                || !text(decision.reasonCode(),80) || !text(decision.reason(),1000) || !text(decision.sourceReference(),1000))
            throw new BadRequestException("INVOICE_TAX_DECISION_REQUIRED","Explicit audited Accounting REQUIRED/NOT_REQUIRED decision required");
        String currency=snapshot.calculation().currency();BigDecimal subtotal=lines.stream().map(RatingLine::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<String,BigDecimal> result=new HashMap<>();
        if(decision.assessmentId()==null) {
            if("REQUIRED".equals(decision.requirement())) throw new BadRequestException("INVOICE_TAX_ASSESSMENT_REQUIRED","Mandatory tax requires audited assessment before financial generation/issue");
            if(!request.lineTaxes().isEmpty()) throw new BadRequestException("INVALID_TAX_ASSESSMENT","NOT_REQUIRED without assessment cannot contain tax allocations");
            for(var line:lines) result.put(lineKey(line.componentType(),line.sourceId()),BigDecimal.ZERO);
            return result; // Audited explicit NOT_REQUIRED, not a guessed tax rate or missing-assessment fallback.
        }
        var assessment=taxes.find(decision.assessmentId()).orElseThrow(()->new BadRequestException("INVOICE_TAX_ASSESSMENT_REQUIRED","Supplied assessment not found in tenant"));
        var a=assessment.assessment();var input=snapshot.calculation().inputs();
        if(!a.loadId().equals(input.loadId()) || !a.customerId().equals(input.matchContext().customerId()))
            throw new BadRequestException("INVALID_TAX_ASSESSMENT","Assessment identity must match accepted billing context");
        CurrencyGuard.requireSameCurrency(currency,a.currency());
        if(a.taxableBasis().compareTo(subtotal)>0) throw new BadRequestException("INVALID_TAX_ASSESSMENT","Assessed taxable basis exceeds selected document subtotal");
        for(var allocation:request.lineTaxes()) {
            if(allocation.componentType()==null || allocation.sourceId()==null || allocation.taxAmount()==null || allocation.taxAmount().signum()<0)
                throw new BadRequestException("INVALID_TAX_ASSESSMENT","Explicit nonnegative per-line tax allocations required");
            exactMoney(allocation.taxAmount(),currency);
            if(result.put(lineKey(allocation.componentType(),allocation.sourceId()),allocation.taxAmount())!=null)
                throw new BadRequestException("INVALID_TAX_ASSESSMENT","Duplicate tax allocation");
        }
        Set<String> expected=new HashSet<>();for(var line:lines) expected.add(lineKey(line.componentType(),line.sourceId()));
        if(!expected.equals(result.keySet()) || a.taxAmount().compareTo(result.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add))!=0)
            throw new BadRequestException("INVALID_TAX_ASSESSMENT","Accounting tax allocations must match selected lines and immutable assessment total exactly");
        return result;
    }
    private String lineKey(String type,UUID id) { return type+":"+id; }
    private boolean text(String value,int max) { return value!=null && !value.isBlank() && value.length()<=max; }
    private void exactMoney(BigDecimal value,String currency) {
        try { value.setScale(scales.scale(currency),RoundingMode.UNNECESSARY); }
        catch(ArithmeticException ex) { throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Financial amount exceeds currency precision"); }
    }
    private BillingInvoice primary(UUID id,GenerateInvoiceRequest r,AcceptedRatingSnapshot s,UUID actor) {
        var p=s.calculation();var allocations=assess(r,s,p.lines());List<BillingInvoice.Line> lines=new ArrayList<>();
        for(var line:p.lines()) lines.add(new BillingInvoice.Line(UUID.randomUUID(),line.componentType(),line.sourceId(),line.description(),
                line.amount(),allocations.get(lineKey(line.componentType(),line.sourceId())),null,null));
        BigDecimal tax=lines.stream().map(BillingInvoice.Line::taxAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new BillingInvoice(id,p.inputs().loadId(),p.inputs().matchContext().customerId(),p.currency(),"PRIMARY",1,"DRAFT",s.snapshotId(),
                null,id,p.subtotal(),tax,p.subtotal().add(tax),lines,UUID.randomUUID(),actor,now());
    }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }

    @Transactional
    public BillingInvoice generatePrimary(GenerateInvoiceRequest request,UUID actor,String correlation) {
        var r=normalize(request);actor(actor);String operation="INVOICE_PRIMARY",hash=fingerprints.hash(r);
        var previous=replay(operation,r.idempotencyKey(),hash);if(previous!=null) return previous;
        var snapshot=accepted(r.snapshotId());billing.lockLoad(snapshot.calculation().inputs().loadId());
        var context=snapshot.calculation().inputs();
        if(billing.primaryExists(context.loadId(),context.matchContext().customerId(),snapshot.calculation().currency()))
            throw conflict("INVOICE_PRIMARY_EXISTS","Load already has a primary or unclassified legacy invoice; no inferred legacy adoption");
        var outcome=primary(UUID.randomUUID(),r,snapshot,actor);billing.insert(outcome,r);billing.claimCharges(outcome);billing.command(operation,r.idempotencyKey(),hash,r,outcome);
        log.info("invoice_command success=true operation={} invoiceId={} policyVersion={} correlationId={}",operation,outcome.invoiceId(),snapshot.calculation().roundingPolicyVersion(),correlation);
        return outcome;
    }
    @Transactional
    public BillingInvoice regenerateDraft(UUID id,UUID expectedSnapshot,GenerateInvoiceRequest request,UUID actor) {
        var r=normalize(request);actor(actor);String operation="INVOICE_REGENERATE";
        Map<String,Object> input=new LinkedHashMap<>();input.put("invoiceId",id);input.put("expectedSnapshotId",expectedSnapshot);input.put("request",r);
        String hash=fingerprints.hash(input);var previous=replay(operation,r.idempotencyKey(),hash);if(previous!=null) return previous;
        billing.lockLoad(billing.current(id,false).loadId());var current=billing.current(id,true);
        if(!"DRAFT".equals(current.status()) || !"PRIMARY".equals(current.purpose())) throw conflict("INVOICE_HISTORY_IMMUTABLE","Only unissued primary DRAFT may regenerate");
        if(expectedSnapshot==null || !expectedSnapshot.equals(current.snapshotId())) throw conflict("INVOICE_BASIS_STALE","Draft rating reference changed");
        var snapshot=accepted(r.snapshotId());var outcome=primary(id,r,snapshot,actor);
        if(!current.loadId().equals(outcome.loadId()) || !current.customerId().equals(outcome.customerId()))
            throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Regeneration cannot replace invoice business identity");
        CurrencyGuard.requireSameCurrency(current.currency(),outcome.currency());
        billing.updateDraft(outcome,r);billing.claimCharges(outcome);billing.command(operation,r.idempotencyKey(),hash,input,outcome);return outcome;
    }
    @Transactional
    public BillingInvoice issue(UUID id,String key,UUID actor) {
        key(key);actor(actor);String operation="INVOICE_ISSUE";Map<String,Object> input=Map.of("invoiceId",id);
        String hash=fingerprints.hash(input);var previous=replay(operation,key,hash);if(previous!=null) return previous;
        settlementRevenue.lockIssueSources(billing.current(id,false).loadId());var current=billing.current(id,true);
        if(!"DRAFT".equals(current.status())) throw conflict("INVOICE_ALREADY_ISSUED","Invoice must be unissued DRAFT");
        var outcome=new BillingInvoice(current.invoiceId(),current.loadId(),current.customerId(),current.currency(),current.purpose(),current.economicSign(),"ISSUED",
                current.snapshotId(),current.parentInvoiceId(),current.billingChainId(),current.subtotal(),current.tax(),current.total(),current.lines(),UUID.randomUUID(),actor,now());
        billing.issue(outcome);billing.command(operation,key,hash,input,outcome);
        settlementRevenue.issued(outcome.invoiceId(),outcome.loadId(),actor);return outcome;
    }
    @Transactional(readOnly=true)
    public BillingInvoice get(UUID id) { return billing.current(id,false); }

    private void reason(String code,String reason) {
        if(!text(code,80) || !text(reason,1000)) throw new BadRequestException("INVOICE_CORRECTION_REASON_REQUIRED","Explicit reasonCode and reason required");
    }
    private List<UUID> ids(List<UUID> values,String name) {
        if(values==null || values.isEmpty() || values.stream().anyMatch(Objects::isNull) || new HashSet<>(values).size()!=values.size())
            throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Explicit unique "+name+" required");
        return values.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }
    private BillingInvoice parent(UUID id) {
        var preliminary=billing.current(id,false);billing.lockLoad(preliminary.loadId());
        billing.current(preliminary.billingChainId(),true);var p=billing.current(id,true);
        if(p.economicSign()!=1 || !com.company.logicstic.common.enums.InvoiceStatus.fromString(p.status()).countsAsRevenue())
            throw new BadRequestException("INVOICE_CORRECTION_PARENT_REQUIRED","Correction requires eligible issued positive document");
        return p;
    }
    private void context(BillingInvoice parent,AcceptedRatingSnapshot s) {
        var i=s.calculation().inputs();
        if(!i.loadId().equals(parent.loadId()) || !i.matchContext().customerId().equals(parent.customerId()))
            throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Correction accepted snapshot has different Load/customer");
        CurrencyGuard.requireSameCurrency(parent.currency(),s.calculation().currency());
    }
    private BillingInvoice correction(UUID id,String purpose,BillingInvoice parent,AcceptedRatingSnapshot s,
            GenerateInvoiceRequest r,List<RatingLine> selected,List<BillingCorrectionRequests.CreditLine> creditLines,UUID actor) {
        var tax=assess(r,s,selected);List<BillingInvoice.Line> lines=new ArrayList<>();
        for(var l:selected) {
            var credit=creditLines.stream().filter(x->x.originalLineId().equals(l.sourceId())).findFirst().orElse(null);
            lines.add(new BillingInvoice.Line(UUID.randomUUID(),l.componentType(),l.sourceId(),l.description(),l.amount(),
                    tax.get(lineKey(l.componentType(),l.sourceId())),credit==null?null:credit.originalLineId(),credit==null?null:credit.quantity()));
        }
        BigDecimal subtotal=lines.stream().map(BillingInvoice.Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal taxTotal=lines.stream().map(BillingInvoice.Line::taxAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new BillingInvoice(id,parent.loadId(),parent.customerId(),parent.currency(),purpose,"CREDIT".equals(purpose)?-1:1,"DRAFT",
                s.snapshotId(),parent.invoiceId(),parent.billingChainId(),subtotal,taxTotal,subtotal.add(taxTotal),lines,UUID.randomUUID(),actor,now());
    }
    @Transactional
    public BillingInvoice supplemental(UUID parentId,BillingCorrectionRequests.Supplemental request,UUID actor) {
        if(request==null) throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Explicit supplemental command required");
        var generation=normalize(request.generation());reason(request.reasonCode(),request.reason());var charges=ids(request.chargeIds(),"approved charge events");actor(actor);
        var normalized=new BillingCorrectionRequests.Supplemental(generation,charges,request.reasonCode(),request.reason());
        var input=Map.of("parentInvoiceId",parentId,"request",normalized);String hash=fingerprints.hash(input),operation="INVOICE_SUPPLEMENTAL";
        var prior=replay(operation,generation.idempotencyKey(),hash);if(prior!=null)return prior;
        var p=parent(parentId);if(!"PRIMARY".equals(p.purpose())) throw new BadRequestException("INVOICE_CORRECTION_PARENT_REQUIRED","Supplemental parent must be PRIMARY");
        var s=accepted(generation.snapshotId());context(p,s);
        var selected=s.calculation().lines().stream().filter(l->"ACCESSORIAL".equals(l.componentType()) && charges.contains(l.sourceId())).toList();
        if(selected.size()!=charges.size() || selected.stream().anyMatch(l->l.amount().signum()<=0))
            throw new BadRequestException("INVOICE_INCREMENTAL_CHARGE_REQUIRED","Supplemental selects only positive approved incremental charge events from accepted snapshot, not linehaul/FSC");
        var outcome=correction(UUID.randomUUID(),"SUPPLEMENTAL",p,s,generation,selected,List.of(),actor);
        billing.insert(outcome,generation);billing.claimCharges(outcome);billing.command(operation,generation.idempotencyKey(),hash,input,outcome);return outcome;
    }
    @Transactional
    public BillingInvoice credit(UUID parentId,BillingCorrectionRequests.Credit request,UUID actor) {
        if(request==null || request.lines()==null || request.lines().isEmpty() || request.lines().stream().anyMatch(Objects::isNull))
            throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Explicit original credited lines required");
        key(request.idempotencyKey());reason(request.reasonCode(),request.reason());actor(actor);
        var lines=request.lines().stream().sorted(Comparator.comparing(l->Objects.toString(l.originalLineId(),""))).toList();
        if(lines.stream().map(BillingCorrectionRequests.CreditLine::originalLineId).distinct().count()!=lines.size())
            throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Duplicate credited original line");
        var normalized=new BillingCorrectionRequests.Credit(request.idempotencyKey(),lines,request.taxDecision(),request.reasonCode(),request.reason());
        var input=Map.of("parentInvoiceId",parentId,"request",normalized);String operation="INVOICE_CREDIT",hash=fingerprints.hash(input);
        var prior=replay(operation,request.idempotencyKey(),hash);if(prior!=null) return prior;
        var p=parent(parentId);var s=accepted(p.snapshotId());List<RatingLine> selected=new ArrayList<>();List<GenerateInvoiceRequest.LineTax> allocations=new ArrayList<>();
        for(var l:lines) {
            var original=p.lines().stream().filter(x->x.lineId().equals(l.originalLineId())).findFirst()
                    .orElseThrow(()->new BadRequestException("INVOICE_CREDIT_LINE_REQUIRED","Credited line must belong to parent"));
            if(l.amount()==null || l.amount().signum()<=0 || l.taxAmount()==null || l.taxAmount().signum()<0
                    || (l.quantity()!=null && l.quantity().signum()<=0))
                throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Credit amount/optional quantity must be positive, tax nonnegative");
            exactMoney(l.amount(),p.currency());exactMoney(l.taxAmount(),p.currency());
            if(billing.credited(original.lineId(),"amount_amount").add(l.amount()).compareTo(original.amount())>0
                    || billing.credited(original.lineId(),"tax_amount").add(l.taxAmount()).compareTo(original.taxAmount())>0
                    || (l.quantity()!=null && billing.credited(original.lineId(),"credited_quantity").add(l.quantity()).compareTo(BigDecimal.ONE)>0))
                throw conflict("INVOICE_OVER_CREDIT","Cumulative reserved/issued line credit cannot exceed original amount/tax/quantity");
            String description="Credit: "+original.description();if(description.length()>500)description=description.substring(0,500);
            selected.add(new RatingLine("CREDIT",original.lineId(),description,l.amount(),l.amount(),p.currency()));
            allocations.add(new GenerateInvoiceRequest.LineTax("CREDIT",original.lineId(),l.taxAmount()));
        }
        boolean supplied=request.taxDecision()!=null && request.taxDecision().assessmentId()!=null;
        if(!supplied && lines.stream().anyMatch(l->l.taxAmount().signum()!=0)) throw new BadRequestException("INVOICE_TAX_ASSESSMENT_REQUIRED","Credit tax requires separate audited assessment");
        var generation=new GenerateInvoiceRequest(request.idempotencyKey(),s.snapshotId(),request.taxDecision(),supplied?allocations:List.of());
        var outcome=correction(UUID.randomUUID(),"CREDIT",p,s,generation,selected,lines,actor);
        billing.insert(outcome,generation);billing.command(operation,request.idempotencyKey(),hash,input,outcome);return outcome;
    }
    @Transactional
    public BillingInvoice rebill(UUID originalId,BillingCorrectionRequests.Rebill request,UUID actor) {
        if(request==null) throw new BadRequestException("INVOICE_VALIDATION_REQUIRED","Explicit rebill command required");
        var generation=normalize(request.generation());reason(request.reasonCode(),request.reason());var evidence=ids(request.creditEvidenceIds(),"full-reversal credit evidence IDs");actor(actor);
        var normalized=new BillingCorrectionRequests.Rebill(generation,evidence,request.reasonCode(),request.reason());
        var input=Map.of("parentInvoiceId",originalId,"request",normalized);String hash=fingerprints.hash(input),operation="INVOICE_REBILL";
        var prior=replay(operation,generation.idempotencyKey(),hash);if(prior!=null)return prior;
        var p=parent(originalId);if(billing.rebilled(originalId))throw conflict("INVOICE_REBILL_EXISTS","Original already has a full replacement");
        BigDecimal subtotal=BigDecimal.ZERO,tax=BigDecimal.ZERO;
        for(UUID id:evidence) {
            var credit=billing.current(id,true);
            if(!"CREDIT".equals(credit.purpose()) || !originalId.equals(credit.parentInvoiceId()) || !p.billingChainId().equals(credit.billingChainId())
                    || !com.company.logicstic.common.enums.InvoiceStatus.fromString(credit.status()).countsAsRevenue())
                throw new BadRequestException("INVOICE_FULL_CREDIT_REQUIRED","Every evidence ID must be an eligible issued credit of this original");
            subtotal=subtotal.add(credit.subtotal());tax=tax.add(credit.tax());
        }
        if(subtotal.compareTo(p.subtotal())!=0 || tax.compareTo(p.tax())!=0)
            throw new BadRequestException("INVOICE_FULL_CREDIT_REQUIRED","Rebill requires exact full economic subtotal and tax reversal, including multi-partial evidence");
        var s=accepted(generation.snapshotId());context(p,s);
        if(s.snapshotId().equals(p.snapshotId())) throw new BadRequestException("INVOICE_NEW_RATING_REQUIRED","Rebill requires a new accepted snapshot, never the original basis");
        var outcome=correction(UUID.randomUUID(),"REBILL",p,s,generation,s.calculation().lines(),List.of(),actor);
        billing.insert(outcome,generation);billing.claimCharges(outcome);billing.creditEvidence(outcome.invoiceId(),evidence);
        billing.command(operation,generation.idempotencyKey(),hash,input,outcome);return outcome;
    }
}
