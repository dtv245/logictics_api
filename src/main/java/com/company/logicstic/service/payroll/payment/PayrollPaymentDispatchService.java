package com.company.logicstic.service.payroll.payment;
import com.company.logicstic.repository.*;
import com.company.logicstic.entity.PayrollPayment;
import com.company.logicstic.dto.payroll.PayrollPaymentView;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service
public class PayrollPaymentDispatchService {
 private final PayrollPaymentRepository payments;
 private final PayrollRunRepository runs;
 private final Map<String,PayrollPaymentProvider> providers;
 private final TransactionTemplate transactions;
 public PayrollPaymentDispatchService(PayrollPaymentRepository payments,PayrollRunRepository runs,
     List<PayrollPaymentProvider> registered,PlatformTransactionManager manager) {
  this.payments=payments;this.runs=runs;this.transactions=new TransactionTemplate(manager);
  var map=new HashMap<String,PayrollPaymentProvider>();
  for(var p:registered) if(p.key()==null || map.putIfAbsent(p.key(),p)!=null) throw new IllegalStateException("Duplicate/missing payroll payment provider key");
  this.providers=Map.copyOf(map);
 }
 private record Dispatch(PayrollPaymentView prior,PayrollPaymentInstruction instruction,PayrollPaymentProvider provider) {}
 public PayrollPaymentView dispatch(UUID id) {
  var task=transactions.execute(status -> {
   var p=locked(id);
   if(!"SCHEDULED".equals(p.getStatus())) return new Dispatch(PayrollPaymentView.from(p),null,null);
   if("MANUAL".equals(p.getPaymentMethod())) throw new BadRequestException("PAYMENT_MANUAL_RECONCILIATION_REQUIRED","Manual payments require bank reconciliation");
   var provider=providers.get(p.getProviderKey());
   if(provider==null || !provider.supports(p.getPaymentMethod())) throw new BadRequestException("PAYMENT_PROVIDER_NOT_CONFIGURED","No explicit provider adapter configured");
   p.setStatus("PROCESSING");p.setDispatchStartedAt(now());payments.saveAndFlush(p);
   return new Dispatch(null,new PayrollPaymentInstruction(p.getId(),p.getItem().getId(),p.getItem().getDriver().getId(),
     p.getIdempotencyKey(),p.getPaymentMethod(),p.getCurrency(),p.getAmount(),p.getDestinationReference()),provider);
  });
  if(task.prior()!=null) return task.prior();
  // Intent is committed before I/O. Unknown provider outcome stays PROCESSING for verified reconciliation.
  var submission=task.provider().submit(task.instruction());
  if(submission==null || !"AVAILABLE".equals(submission.availability()) || submission.providerReference()==null
      || submission.providerReference().isBlank() || submission.providerReference().length()>200)
   throw new BadRequestException("PAYMENT_SUBMISSION_OUTCOME_UNKNOWN","Submission outcome requires provider/manual reconciliation; never assume failure");
  return transactions.execute(status -> {
   var p=locked(id);
   if(p.getProviderReference()!=null && !p.getProviderReference().equals(submission.providerReference()))
    throw new BadRequestException("PAYMENT_PROVIDER_REFERENCE_CONFLICT","Provider reference differs from verified payment history");
   if("PROCESSING".equals(p.getStatus())) {p.setProviderReference(submission.providerReference());p.setSubmittedAt(now());payments.saveAndFlush(p);}
   return PayrollPaymentView.from(p);
  });
 }
 private PayrollPayment locked(UUID id) {
  var runId=payments.findPayrollRunId(id).orElseThrow(() -> new BadRequestException("Payroll payment not found"));
  var run=runs.findByIdForUpdate(runId).orElseThrow();
  if(!Set.of("PAYMENT_SCHEDULED","PAID").contains(run.getStatus())) throw new BadRequestException("PAYMENT_RUN_NOT_SCHEDULED","Payroll payment workflow requires scheduled run");
  return payments.findByIdForUpdate(id).orElseThrow();
 }
 private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);}
}
