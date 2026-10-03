package com.company.logicstic.service.payroll.payment;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
public record VerifiedPayrollPaymentEvent(String eventId,UUID paymentId,String outcome,BigDecimal amount,String currency,
 String providerReference,OffsetDateTime occurredAt) {}
