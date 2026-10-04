package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.payment.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.payroll.PayrollReconciliationService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PayrollPaymentOutcomeTest {
 private PayrollPayment payment() {
  var p=new PayrollPayment();p.setId(UUID.randomUUID());p.setStatus("PROCESSING");p.setAmount(new BigDecimal("90"));p.setCurrency("USD");
  p.setProviderKey("fixture");p.setProviderReference("reference");p.setPaymentMethod("BANK_TRANSFER");var item=new PayrollRunItem();item.setId(UUID.randomUUID());p.setItem(item);return p;
 }
 private VerifiedPayrollPaymentEvent event(PayrollPayment p,String outcome,String amount,String currency,String reference) {
  return new VerifiedPayrollPaymentEvent("event",p.getId(),outcome,new BigDecimal(amount),currency,reference,OffsetDateTime.now());
 }
 private PayrollPaymentOutcomeService service() {
  return new PayrollPaymentOutcomeService(mock(PayrollPaymentRepository.class),mock(PayrollRunItemRepository.class),
   mock(DriverSettlementRepository.class),mock(PayrollPaymentEventRepository.class),
   new PayrollReconciliationService(TestRoundingPolicies.standard()),mock(com.company.logicstic.service.payroll.PayrollRunCompletionService.class));
 }
 @Test void financialIdentityMustMatchExactly() {
  var p=payment();var s=service();
  assertEquals("PAYMENT_AMOUNT_MISMATCH",s.assess(p,"fixture",event(p,"SUCCEEDED","89","USD","reference"),false,null).reason());
  assertEquals("PAYMENT_CURRENCY_MISMATCH",s.assess(p,"fixture",event(p,"SUCCEEDED","90","EUR","reference"),false,null).reason());
  assertEquals("PAYMENT_REFERENCE_MISMATCH",s.assess(p,"fixture",event(p,"SUCCEEDED","90","USD","other"),false,null).reason());
  assertEquals("PROCESSING",p.getStatus());
 }
 @Test void untrustedProviderAndLateOutcomesNeverAutomaticallyPay() {
  var p=payment();var s=service();var e=event(p,"SUCCEEDED","90","USD","reference");
  assertEquals("PAYMENT_PROVIDER_IDENTITY_MISMATCH",s.assess(p,"different-provider",e,false,null).reason());
  p.setStatus("FAILED");assertEquals("RECONCILIATION_REQUIRED",s.assess(p,"fixture",e,false,null).status());
  assertFalse(s.assess(p,"fixture",e,false,null).apply());assertEquals("FAILED",p.getStatus());
 }
 @Test void settledPaymentIsIdempotentAndCannotDowngradeOnFailure() {
  var p=payment();p.setStatus("SUCCEEDED");var s=service();
  assertEquals("DUPLICATE",s.assess(p,"fixture",event(p,"SUCCEEDED","90","USD","reference"),false,null).status());
  assertEquals("RECONCILIATION_REQUIRED",s.assess(p,"fixture",event(p,"FAILED","90","USD","reference"),false,null).status());
  assertEquals("SUCCEEDED",p.getStatus());
 }
}
