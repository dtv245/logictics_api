package com.company.logicstic.service.payroll.payment;
/** Submission must be bounded and idempotent by instruction.idempotencyKey, including unknown-outcome retry.
 * An accepted submission is PROCESSING; verified completion is a separate callback/reconciliation port. */
public interface PayrollPaymentProvider {
 String key();
 boolean supports(String paymentMethod);
 Submission submit(PayrollPaymentInstruction instruction);
 record Submission(String availability,String reason,String providerReference) {}
}
