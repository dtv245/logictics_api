package com.company.logicstic.service.payroll.payment;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.exception.*;
import com.company.logicstic.common.CurrencyGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
@Service
public class PayrollCallbackService {
 private final Map<String,PayrollProviderCallbackVerifier> verifiers;
 private final PayrollPaymentRepository payments;
 private final PayrollRunRepository runs;
 private final PayrollPaymentEventRepository events;
 private final PayrollPaymentOutcomeService outcomes;
 private final ObjectMapper json;
 private final TransactionTemplate transactions;
 private final PayrollCallbackTenantScope tenantScope;
 public PayrollCallbackService(List<PayrollProviderCallbackVerifier> registered,PayrollPaymentRepository payments,PayrollRunRepository runs,
   PayrollPaymentEventRepository events,PayrollPaymentOutcomeService outcomes,ObjectMapper json,PlatformTransactionManager manager,
   PayrollCallbackTenantScope tenantScope) {
  var map=new HashMap<String,PayrollProviderCallbackVerifier>();
  for(var v:registered) if(v.key()==null || map.putIfAbsent(v.key(),v)!=null) throw new IllegalStateException("Duplicate/missing payroll callback verifier");
  this.verifiers=Map.copyOf(map);this.payments=payments;this.runs=runs;this.events=events;this.outcomes=outcomes;this.json=json;
  this.transactions=new TransactionTemplate(manager);
  this.tenantScope=tenantScope;
 }
 public PayrollPaymentEventView receive(String provider,String originalBody,Map<String,String> headers) {
  var verifier=verifiers.get(provider);
  if(verifier==null) throw new ForbiddenException("PAYMENT_CALLBACK_VERIFIER_NOT_CONFIGURED");
  var verified=verifier.verify(originalBody,headers);
  if(verified==null || verified.event()==null || verified.verificationProof()==null)
   throw new ForbiddenException("Verified callback provenance required");
  var event=verified.event();
  if(event.eventId()==null || event.eventId().isBlank() || event.eventId().length()>200 || event.paymentId()==null
    || event.outcome()==null || !Set.of("SUCCEEDED","FAILED").contains(event.outcome()) || event.amount()==null || event.amount().signum()<0
    || event.amount().stripTrailingZeros().scale()>4 || event.occurredAt()==null || event.providerReference()==null
    || event.providerReference().isBlank() || event.providerReference().length()>200)
   throw new BadRequestException("PAYMENT_CALLBACK_INVALID","Verified callback must carry explicit financial identity and outcome");
  CurrencyGuard.canonical(event.currency());
  return tenantScope.withTenant(verified.tenantId(),() -> transactions.execute(tx -> {
   var runId=payments.findPayrollRunId(event.paymentId()).orElseThrow(() -> new BadRequestException("Payroll payment not found"));
   var run=runs.findByIdForUpdate(runId).orElseThrow();var payment=payments.findByIdForUpdate(event.paymentId()).orElseThrow();
   String payload=json.writeValueAsString(event);
   var prior=events.findBySourceTypeAndProviderKeyAndSourceKey("PROVIDER",provider,event.eventId());
   if(prior.isPresent()) {
    var old=prior.get();
    if(!old.getPayment().getId().equals(payment.getId()) || !json.readTree(old.getPayloadJson()).equals(json.readTree(payload)))
     throw new BadRequestException("PAYMENT_CALLBACK_IDEMPOTENCY_CONFLICT","Provider event ID already has different financial inputs");
    return view(old,payment);
   }
   var decision=outcomes.assess(payment,provider,event,false,null);
   var receipt=new PayrollPaymentEvent();receipt.setPayment(payment);receipt.setSourceType("PROVIDER");receipt.setSourceKey(event.eventId());
   receipt.setProviderKey(provider);receipt.setOutcome(event.outcome());receipt.setAmount(event.amount());receipt.setCurrency(CurrencyGuard.canonical(event.currency()));
   receipt.setProviderReference(event.providerReference());receipt.setOccurredAt(event.occurredAt());receipt.setStatus(decision.status());receipt.setReason(decision.reason());
   receipt.setPayloadJson(payload);receipt.setVerificationJson(json.writeValueAsString(Map.of("proof",verified.verificationProof(),
     "originalBody",originalBody,"verifiedTenantId",Objects.toString(verified.tenantId(),""))));events.saveAndFlush(receipt);
   if(decision.apply()) outcomes.apply(payment,run,event,null,null);
   return view(receipt,payment);
  }));
 }
 private PayrollPaymentEventView view(PayrollPaymentEvent e,PayrollPayment p) {return new PayrollPaymentEventView(e.getId(),e.getSourceKey(),e.getStatus(),e.getReason(),PayrollPaymentView.from(p));}
}
