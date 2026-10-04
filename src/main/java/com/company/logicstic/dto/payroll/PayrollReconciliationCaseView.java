package com.company.logicstic.dto.payroll;

import com.company.logicstic.entity.PayrollPaymentEvent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PayrollReconciliationCaseView(
        UUID caseEventId,
        UUID paymentId,
        String paymentStatus,
        String sourceKey,
        String providerKey,
        String outcome,
        BigDecimal amount,
        String currency,
        String providerReference,
        OffsetDateTime occurredAt,
        String reason) {

    public static PayrollReconciliationCaseView from(PayrollPaymentEvent event) {
        var payment = event.getPayment();
        return new PayrollReconciliationCaseView(
                event.getId(),
                payment.getId(),
                payment.getStatus(),
                event.getSourceKey(),
                event.getProviderKey(),
                event.getOutcome(),
                event.getAmount(),
                event.getCurrency(),
                event.getProviderReference(),
                event.getOccurredAt(),
                event.getReason());
    }
}
