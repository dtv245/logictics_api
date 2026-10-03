package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.payment.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PayrollPaymentDispatchTest {
 private record Fixture(PayrollPayment payment,PayrollPaymentRepository payments,PayrollRunRepository runs,PlatformTransactionManager manager) {}
 private Fixture fixture() {
  var run=new PayrollRun();run.setId(UUID.randomUUID());run.setStatus("PAYMENT_SCHEDULED");
  var driver=new Employee();driver.setId(UUID.randomUUID());var item=new PayrollRunItem();item.setId(UUID.randomUUID());item.setDriver(driver);item.setPayrollRun(run);
  var p=new PayrollPayment();p.setId(UUID.randomUUID());p.setItem(item);p.setStatus("SCHEDULED");p.setPaymentMethod("BANK_TRANSFER");
  p.setProviderKey("fixture");p.setIdempotencyKey(UUID.randomUUID().toString());p.setCurrency("USD");p.setAmount(BigDecimal.TEN);p.setAttemptNumber(1);
  var payments=mock(PayrollPaymentRepository.class);var runs=mock(PayrollRunRepository.class);var manager=mock(PlatformTransactionManager.class);
  when(payments.findPayrollRunId(p.getId())).thenReturn(Optional.of(run.getId()));when(payments.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));
  when(runs.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
  return new Fixture(p,payments,runs,manager);
 }
 @Test void commitsIntentBeforeProviderIoAndNeverTreatsSubmissionAsPaid() {
  var f=fixture();var provider=mock(PayrollPaymentProvider.class);when(provider.key()).thenReturn("fixture");when(provider.supports("BANK_TRANSFER")).thenReturn(true);
  when(provider.submit(any())).thenAnswer(i -> {
   verify(f.manager()).commit(any());assertEquals("PROCESSING",f.payment().getStatus());
   var input=(PayrollPaymentInstruction)i.getArgument(0);assertEquals(f.payment().getIdempotencyKey(),input.idempotencyKey());
   return new PayrollPaymentProvider.Submission("AVAILABLE",null,"fixture-reference");
  });
  var service=new PayrollPaymentDispatchService(f.payments(),f.runs(),List.of(provider),f.manager());
  var result=service.dispatch(f.payment().getId());assertEquals("PROCESSING",result.status());assertNull(result.succeededAt());
  verify(f.manager(),times(2)).commit(any());
 }
 @Test void unconfiguredProviderDoesNotPersistSubmissionIntent() {
  var f=fixture();var service=new PayrollPaymentDispatchService(f.payments(),f.runs(),List.of(),f.manager());
  assertEquals("PAYMENT_PROVIDER_NOT_CONFIGURED",assertThrows(BadRequestException.class,() -> service.dispatch(f.payment().getId())).getCode());
  assertEquals("SCHEDULED",f.payment().getStatus());verify(f.payments(),never()).saveAndFlush(any());
 }
 @Test void unknownOutcomePreservesCommittedIntentAndDoesNotResubmitOnRetry() {
  var f=fixture();var provider=mock(PayrollPaymentProvider.class);when(provider.key()).thenReturn("fixture");when(provider.supports("BANK_TRANSFER")).thenReturn(true);
  when(provider.submit(any())).thenReturn(new PayrollPaymentProvider.Submission("UNAVAILABLE","fixture-timeout",null));
  var service=new PayrollPaymentDispatchService(f.payments(),f.runs(),List.of(provider),f.manager());
  assertEquals("PAYMENT_SUBMISSION_OUTCOME_UNKNOWN",assertThrows(BadRequestException.class,() -> service.dispatch(f.payment().getId())).getCode());
  assertEquals("PROCESSING",service.dispatch(f.payment().getId()).status());verify(provider,times(1)).submit(any());
 }
}
