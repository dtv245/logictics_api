package com.company.logicstic.service.payroll.payment;
import java.util.UUID;
import java.math.BigDecimal;
public record PayrollPaymentInstruction(UUID paymentId,UUID payrollItemId,UUID driverId,String idempotencyKey,String paymentMethod,
 String currency,BigDecimal amount,String destinationReference) {}
