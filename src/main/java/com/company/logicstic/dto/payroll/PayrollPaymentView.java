package com.company.logicstic.dto.payroll;
import com.company.logicstic.entity.PayrollPayment;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.UUID;
public record PayrollPaymentView(UUID id,UUID payrollItemId,int attemptNumber,String idempotencyKey,String paymentMethod,
 String status,BigDecimal amount,String currency,String providerKey,String providerReference,String failureCode,String failureMessage,
 OffsetDateTime scheduledAt,OffsetDateTime submittedAt,OffsetDateTime succeededAt,OffsetDateTime reconciledAt) {
 public static PayrollPaymentView from(PayrollPayment p) {
  return new PayrollPaymentView(p.getId(),p.getItem().getId(),p.getAttemptNumber(),p.getIdempotencyKey(),p.getPaymentMethod(),p.getStatus(),
    p.getAmount(),p.getCurrency(),p.getProviderKey(),p.getProviderReference(),p.getFailureCode(),p.getFailureMessage(),
    p.getScheduledAt(),p.getSubmittedAt(),p.getSucceededAt(),p.getReconciledAt());
 }
}
