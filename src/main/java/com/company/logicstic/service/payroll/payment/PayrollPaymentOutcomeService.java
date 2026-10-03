package com.company.logicstic.service.payroll.payment;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.service.payroll.PayrollReconciliationService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service @RequiredArgsConstructor
public class PayrollPaymentOutcomeService {
 private final PayrollPaymentRepository payments;
 private final PayrollRunItemRepository items;
 private final DriverSettlementRepository settlements;
 private final PayrollRunRepository runs;
 private final PayrollPaymentEventRepository events;
 private final PayrollReconciliationService reconciliation;
 public record Decision(String status,String reason,boolean apply) {}
 public Decision assess(PayrollPayment p,String provider,VerifiedPayrollPaymentEvent event,boolean bank,UUID resolvesEventId) {
  if(!bank && ("MANUAL".equals(p.getPaymentMethod()) || !Objects.equals(provider,p.getProviderKey())))
   return new Decision("REJECTED","PAYMENT_PROVIDER_IDENTITY_MISMATCH",false);
  if(!CurrencyGuard.canonical(p.getCurrency()).equals(CurrencyGuard.canonical(event.currency())))
   return new Decision("REJECTED","PAYMENT_CURRENCY_MISMATCH",false);
  if(event.amount()==null || event.amount().compareTo(p.getAmount())!=0)
   return new Decision("REJECTED","PAYMENT_AMOUNT_MISMATCH",false);
  if(p.getProviderReference()!=null && !Objects.equals(p.getProviderReference(),event.providerReference()))
   return new Decision("REJECTED","PAYMENT_REFERENCE_MISMATCH",false);
  if("SUCCEEDED".equals(p.getStatus())) return new Decision("SUCCEEDED".equals(event.outcome())?"DUPLICATE":"RECONCILIATION_REQUIRED",
    "SUCCEEDED".equals(event.outcome())?null:"PAYMENT_LATE_FAILURE_REQUIRES_RECONCILIATION",false);
  if("FAILED".equals(p.getStatus()) && "FAILED".equals(event.outcome())) return new Decision("DUPLICATE",null,false);
  if("FAILED".equals(p.getStatus()) || "CANCELLED".equals(p.getStatus()))
   return new Decision("RECONCILIATION_REQUIRED","PAYMENT_LATE_OUTCOME_REQUIRES_RECONCILIATION",false);
  if(!Set.of("PROCESSING","SUBMITTED").contains(p.getStatus()) && !(bank && "SCHEDULED".equals(p.getStatus())))
   return new Decision("REJECTED","PAYMENT_NOT_SUBMITTED",false);
  if("SUCCEEDED".equals(event.outcome()) && events.hasUnresolvedCase(p.getItem().getId()) && resolvesEventId==null)
   return new Decision("RECONCILIATION_REQUIRED","PAYMENT_CASE_UNRESOLVED",false);
  return new Decision("APPLIED",null,true);
 }
 /** Called inside the same transaction after a matching immutable APPLIED evidence row has been inserted. */
 public void apply(PayrollPayment payment,PayrollRun run,VerifiedPayrollPaymentEvent event,UUID bankActor,String bankReference) {
  var item=items.findByIdForUpdate(payment.getItem().getId()).orElseThrow();
  reconciliation.requireFinalizable(run,items.findByPayrollRunIdOrderById(run.getId()));
  payment.setProviderReference(event.providerReference());payment.setStatus(event.outcome());
  if(bankActor!=null) {payment.setReconciledBy(bankActor);payment.setReconciliationReference(bankReference);payment.setReconciledAt(now());}
  if("SUCCEEDED".equals(event.outcome())) {
   if(!"PAYMENT_PENDING".equals(item.getStatus())) throw new com.company.logicstic.exception.BadRequestException("PAYMENT_ITEM_NOT_PENDING","Successful evidence must match pending payroll item");
   payment.setSucceededAt(event.occurredAt().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
   payments.saveAndFlush(payment);item.setStatus("PAID");items.saveAndFlush(item);
   for(var id:item.getSettlements().stream().map(DriverSettlement::getId).sorted().toList()) {
    var s=settlements.findByIdForUpdate(id).orElseThrow();
    if(!"PAYMENT_SCHEDULED".equals(s.getStatus())) throw new com.company.logicstic.exception.BadRequestException("PAYMENT_SETTLEMENT_NOT_SCHEDULED","Mapped settlement must be scheduled before paid");
    s.setStatus("PAID");s.setPaidAt(payment.getSucceededAt());settlements.saveAndFlush(s);
   }
   if(items.findByPayrollRunIdOrderById(run.getId()).stream().allMatch(i -> "PAID".equals(i.getStatus()))) {
    run.setStatus("PAID");run.setPaidAt(now());runs.saveAndFlush(run);
   }
  } else {
   payment.setFailureCode("VERIFIED_PAYMENT_FAILURE");payment.setFailureMessage("Failure confirmed by "+(bankActor==null?"provider":"bank reconciliation"));
   payments.saveAndFlush(payment);item.setStatus("PAYMENT_FAILED");items.saveAndFlush(item);
  }
 }
 private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);}
}
