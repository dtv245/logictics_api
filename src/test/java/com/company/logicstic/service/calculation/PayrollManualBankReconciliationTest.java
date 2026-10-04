package com.company.logicstic.service.calculation;

import com.company.logicstic.dto.payroll.ManualPayrollBankReconciliationRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.PayrollPayment;
import com.company.logicstic.entity.PayrollPaymentEvent;
import com.company.logicstic.entity.PayrollRun;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.PayrollPaymentEventRepository;
import com.company.logicstic.repository.PayrollPaymentRepository;
import com.company.logicstic.repository.PayrollRunRepository;
import com.company.logicstic.service.payroll.payment.PayrollManualBankReconciliationService;
import com.company.logicstic.service.payroll.payment.PayrollPaymentOutcomeService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayrollManualBankReconciliationTest {
    private record Fixture(
            PayrollManualBankReconciliationService service,
            PayrollPayment payment,
            PayrollPaymentEvent caseEvent,
            PayrollPaymentEventRepository events,
            PayrollPaymentOutcomeService outcomes,
            UUID actorId) {}

    private Fixture fixture(String paymentStatus) {
        var actorId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var item = new com.company.logicstic.entity.PayrollRunItem();
        item.setId(UUID.randomUUID());
        var payment = new PayrollPayment();
        payment.setId(paymentId);
        payment.setItem(item);
        payment.setStatus(paymentStatus);
        payment.setAttemptNumber(1);
        payment.setIdempotencyKey("payment-idem");
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setDestinationReference("account-123");
        payment.setProviderReference("provider-reference");
        payment.setAmount(new BigDecimal("125.2500"));
        payment.setCurrency("USD");

        var caseEvent = new PayrollPaymentEvent();
        caseEvent.setId(UUID.randomUUID());
        caseEvent.setPayment(payment);
        caseEvent.setStatus("RECONCILIATION_REQUIRED");
        caseEvent.setAmount(payment.getAmount());
        caseEvent.setCurrency("USD");

        var payments = mock(PayrollPaymentRepository.class);
        var runs = mock(PayrollRunRepository.class);
        var events = mock(PayrollPaymentEventRepository.class);
        var employees = mock(EmployeeRepository.class);
        var outcomes = mock(PayrollPaymentOutcomeService.class);
        var json = mock(ObjectMapper.class);
        var run = new PayrollRun();
        run.setId(runId);
        when(payments.findPayrollRunId(paymentId)).thenReturn(Optional.of(runId));
        when(runs.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(events.findBySourceTypeAndProviderKeyAndSourceKey("BANK", null, "idem-1"))
                .thenReturn(Optional.empty());
        when(events.findAppliedBankTransactionId("bank-tx-1")).thenReturn(Optional.empty());
        when(events.findByIdForUpdate(caseEvent.getId())).thenReturn(Optional.of(caseEvent));
        when(events.hasAppliedResolution(caseEvent.getId(), "APPLIED")).thenReturn(false);
        when(employees.existsById(actorId)).thenReturn(true);
        when(json.writeValueAsString(any())).thenReturn("{}");
        var service = new PayrollManualBankReconciliationService(
                payments, runs, events, employees, outcomes, json);
        return new Fixture(service, payment, caseEvent, events, outcomes, actorId);
    }

    private ManualPayrollBankReconciliationRequest request(UUID caseId, String outcome, String amount) {
        return new ManualPayrollBankReconciliationRequest(
                "idem-1", caseId, "bank-main", "bank-tx-1", new BigDecimal(amount), "usd", outcome,
                "account-123", "statement:2026-10-01:row-17", "Statement confirms transaction outcome",
                OffsetDateTime.parse("2026-10-01T10:20:30+07:00"));
    }

    @Test
    void appliesAuditedSuccessOnlyAfterMatchingOpenCaseAndBankEvidence() {
        var fixture = fixture("FAILED");
        when(fixture.events().saveAndFlush(any(PayrollPaymentEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixture.service().reconcile(
                fixture.payment().getId(), request(fixture.caseEvent().getId(), "SUCCEEDED", "125.2500"),
                fixture.actorId());

        assertEquals("APPLIED", result.status());
        verify(fixture.events()).saveAndFlush(argThat(event ->
                "BANK".equals(event.getSourceType())
                        && fixture.actorId().equals(event.getActorId())
                        && fixture.caseEvent().equals(event.getResolvesEvent())
                        && "bank-tx-1".equals(event.getProviderReference())
                        && "125.2500".equals(event.getAmount().toPlainString())
                        && "USD".equals(event.getCurrency())));
        verify(fixture.outcomes()).apply(any(PayrollPayment.class), any(PayrollRun.class),
                any(), eq(fixture.actorId()), eq("bank-tx-1"));
    }

    @Test
    void rejectsAmountDriftWithoutWritingEvidenceOrChangingPayment() {
        var fixture = fixture("FAILED");

        var error = assertThrows(BadRequestException.class, () -> fixture.service().reconcile(
                fixture.payment().getId(), request(fixture.caseEvent().getId(), "SUCCEEDED", "125.26"),
                fixture.actorId()));

        assertEquals("PAYMENT_AMOUNT_MISMATCH", error.getCode());
        verify(fixture.events(), never()).saveAndFlush(any());
        verify(fixture.outcomes(), never()).apply(any(), any(), any(), any(), any());
        assertEquals("FAILED", fixture.payment().getStatus());
    }

    @Test
    void neverDowngradesAnAlreadySuccessfulPayment() {
        var fixture = fixture("SUCCEEDED");

        var error = assertThrows(BadRequestException.class, () -> fixture.service().reconcile(
                fixture.payment().getId(), request(fixture.caseEvent().getId(), "FAILED", "125.2500"),
                fixture.actorId()));

        assertEquals("PAYMENT_SUCCESS_HISTORY_IMMUTABLE", error.getCode());
        verify(fixture.events(), never()).saveAndFlush(any());
        verify(fixture.outcomes(), never()).apply(any(), any(), any(), any(), any());
    }
}
