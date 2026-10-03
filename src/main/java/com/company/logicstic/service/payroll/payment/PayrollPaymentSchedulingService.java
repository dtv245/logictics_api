package com.company.logicstic.service.payroll.payment;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.service.payroll.PayrollReconciliationService;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
@Service @RequiredArgsConstructor
public class PayrollPaymentSchedulingService {
 private final PayrollRunRepository runs;
 private final PayrollRunItemRepository items;
 private final PayrollPaymentRepository payments;
 private final PayslipRepository payslips;
 private final DriverSettlementRepository settlements;
 private final EmployeeRepository employees;
 private final PayrollReconciliationService reconciliation;
 private final ObjectMapper json;
 @Transactional
 public PayrollPaymentView schedule(UUID itemId,SchedulePayrollPaymentRequest request,UUID actor) {
  if(actor==null || !employees.existsById(actor)) throw new BadRequestException("PAYMENT_ACTOR_REQUIRED","Persisted payroll actor required");
  validate(request);
  var runId=items.findPayrollRunId(itemId).orElseThrow(() -> new BadRequestException("Payroll item not found"));
  var run=runs.findByIdForUpdate(runId).orElseThrow();
  var item=items.findByIdForUpdate(itemId).orElseThrow();
  var prior=payments.findByIdempotencyKey(request.idempotencyKey());
  if(prior.isPresent()) {
   var p=prior.get();var original=json.readTree(p.getRequestSnapshotJson()).get("request");
   if(!p.getItem().getId().equals(itemId) || !original.equals(json.readTree(json.writeValueAsString(request))))
    throw new BadRequestException("PAYMENT_IDEMPOTENCY_CONFLICT","Payment key already has different inputs");
   return PayrollPaymentView.from(p);
  }
  if(!Set.of("LOCKED","PAYMENT_SCHEDULED").contains(run.getStatus())) throw new BadRequestException("PAYMENT_PAYROLL_NOT_LOCKED","Payment scheduling requires locked payroll");
  reconciliation.requireFinalizable(run,items.findByPayrollRunIdOrderById(run.getId()));
  if(payslips.findByItemId(itemId).isEmpty()) throw new BadRequestException("PAYMENT_PAYSLIP_REQUIRED","Immutable payslip must be issued before payment");
  if(item.getNetAmount().signum()==0) throw new BadRequestException("PAYMENT_ZERO_NET_DISPOSITION_REQUIRED","Zero-net item requires an explicit no-payment completion policy; do not fabricate a transfer");
  var attempts=payments.findByItemIdOrderByAttemptNumberAsc(itemId);
  if(attempts.stream().anyMatch(p -> Set.of("SCHEDULED","SUBMITTED","PROCESSING","RECONCILIATION_REQUIRED","SUCCEEDED").contains(p.getStatus())))
   throw new BadRequestException("PAYMENT_ATTEMPT_ACTIVE","Existing payment outcome must be resolved before a new attempt");
  if(!Set.of("CALCULATED","SCHEDULED","PAYMENT_FAILED").contains(item.getStatus()))
   throw new BadRequestException("PAYMENT_ITEM_NOT_ELIGIBLE","Item has already been scheduled/paid");
  var destination=request.destinationReference();
  if("STRIPE".equals(request.paymentMethod())) {
   var actual=item.getDriver().getStripeConnectedAccountId();
   if(actual==null || actual.isBlank() || destination!=null && !destination.equals(actual))
    throw new BadRequestException("PAYMENT_DESTINATION_NOT_CONFIGURED","Explicit employee connected-account source is required");
   destination=actual;
  }
  if("BANK_TRANSFER".equals(request.paymentMethod()) && (destination==null || destination.isBlank()))
   throw new BadRequestException("PAYMENT_DESTINATION_NOT_CONFIGURED","Explicit bank/provider account reference required");
  var payment=new PayrollPayment();payment.setId(UUID.randomUUID());payment.setItem(item);
  payment.setAttemptNumber(attempts.stream().mapToInt(PayrollPayment::getAttemptNumber).max().orElse(0)+1);
  payment.setIdempotencyKey(request.idempotencyKey());payment.setPaymentMethod(request.paymentMethod());payment.setProviderKey(request.providerKey());
  payment.setDestinationReference(destination);payment.setStatus("SCHEDULED");payment.setAmount(item.getNetAmount());payment.setCurrency(item.getCurrency());
  payment.setScheduledAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));payment.setScheduledBy(actor);
  var snapshot=new LinkedHashMap<String,Object>();snapshot.put("request",request);snapshot.put("driverId",item.getDriver().getId());
  snapshot.put("payrollRunId",run.getId());snapshot.put("policyId",item.getPayrollPolicy().getId());snapshot.put("policyVersion",item.getPayrollPolicyVersion());
  snapshot.put("amount",payment.getAmount());snapshot.put("currency",payment.getCurrency());snapshot.put("destinationReference",destination);
  snapshot.put("scheduledBy",actor);snapshot.put("scheduledAt",payment.getScheduledAt());payment.setRequestSnapshotJson(json.writeValueAsString(snapshot));
  payments.saveAndFlush(payment);
  for(var id:item.getSettlements().stream().map(DriverSettlement::getId).sorted().toList()) {
   var source=settlements.findByIdForUpdate(id).orElseThrow();
   if(!Set.of("LOCKED","PAYMENT_SCHEDULED").contains(source.getStatus())) throw new BadRequestException("PAYMENT_SETTLEMENT_NOT_ELIGIBLE","Mapped settlement must remain locked/unpaid");
   source.setStatus("PAYMENT_SCHEDULED");settlements.saveAndFlush(source);
  }
  item.setStatus("PAYMENT_PENDING");items.saveAndFlush(item);run.setStatus("PAYMENT_SCHEDULED");runs.saveAndFlush(run);
  return PayrollPaymentView.from(payment);
 }
 @Transactional(readOnly=true)
 public List<PayrollPaymentView> list(UUID itemId) {return payments.findByItemIdOrderByAttemptNumberAsc(itemId).stream().map(PayrollPaymentView::from).toList();}
 private void validate(SchedulePayrollPaymentRequest r) {
  if(r==null || r.idempotencyKey()==null || r.idempotencyKey().isBlank() || r.idempotencyKey().length()>120
   || r.paymentMethod()==null || !Set.of("MANUAL","BANK_TRANSFER","STRIPE").contains(r.paymentMethod())
   || r.providerKey()!=null && r.providerKey().length()>100 || r.destinationReference()!=null && r.destinationReference().length()>200)
   throw new BadRequestException("PAYMENT_REQUEST_INVALID","Explicit valid payment method/request key/destination required");
  if(!"MANUAL".equals(r.paymentMethod()) && (r.providerKey()==null || r.providerKey().isBlank()))
   throw new BadRequestException("PAYMENT_PROVIDER_NOT_CONFIGURED","Automatic payment requires an explicit provider key");
 }
}
