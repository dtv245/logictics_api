package com.company.logicstic.service.payroll.payment;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.payroll.ManualPayrollBankReconciliationRequest;
import com.company.logicstic.dto.payroll.PayrollPaymentEventView;
import com.company.logicstic.dto.payroll.PayrollReconciliationCaseView;
import com.company.logicstic.entity.PayrollPaymentEvent;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.PayrollPaymentEventRepository;
import com.company.logicstic.repository.PayrollPaymentRepository;
import com.company.logicstic.repository.PayrollRunRepository;
import com.company.logicstic.service.payroll.payment.VerifiedPayrollPaymentEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PayrollManualBankReconciliationService {
    private final PayrollPaymentRepository payments;
    private final PayrollRunRepository runs;
    private final PayrollPaymentEventRepository events;
    private final EmployeeRepository employees;
    private final PayrollPaymentOutcomeService outcomes;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public Page<PayrollReconciliationCaseView> openCases(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        return events.findOpenReconciliationCases(PageRequest.of(safePage, safeSize))
                .map(PayrollReconciliationCaseView::from);
    }

    @Transactional
    public PayrollPaymentEventView reconcile(
            UUID paymentId,
            ManualPayrollBankReconciliationRequest request,
            UUID actorId) {
        validate(request, actorId);

        var runId = payments.findPayrollRunId(paymentId)
                .orElseThrow(() -> new BadRequestException("PAYROLL_PAYMENT_NOT_FOUND", "Payroll payment not found"));
        var run = runs.findByIdForUpdate(runId)
                .orElseThrow(() -> new BadRequestException("PAYROLL_RUN_NOT_FOUND", "Payroll run not found"));
        var payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BadRequestException("PAYROLL_PAYMENT_NOT_FOUND", "Payroll payment not found"));

        var normalizedAmount = normalizeAmount(request.amount());
        var normalizedCurrency = CurrencyGuard.canonical(request.currency());
        var occurredAt = normalizeTimestamp(request.occurredAt());
        var payload = payload(request, normalizedAmount, normalizedCurrency, occurredAt);
        var existing = events.findBySourceTypeAndProviderKeyAndSourceKey(
                "BANK", null, request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), paymentId, actorId, payload, payment);
        }

        var reusedTransaction = events.findAppliedBankTransactionId(request.transactionReference());
        if (reusedTransaction.isPresent()) {
            throw new BadRequestException("BANK_TRANSACTION_ALREADY_CLAIMED", "Bank transaction is already claimed by another reconciliation");
        }

        var caseEvent = events.findByIdForUpdate(request.caseEventId())
                .orElseThrow(() -> new BadRequestException("PAYMENT_RECONCILIATION_CASE_NOT_FOUND", "Reconciliation case not found"));
        if (!"RECONCILIATION_REQUIRED".equals(caseEvent.getStatus())
                || events.hasAppliedResolution(caseEvent.getId(), "APPLIED")) {
            throw new BadRequestException("PAYMENT_RECONCILIATION_CASE_CLOSED", "Reconciliation case is not open");
        }
        if (!paymentId.equals(caseEvent.getPayment().getId())) {
            throw new BadRequestException("PAYMENT_RECONCILIATION_CASE_MISMATCH", "Case belongs to another payment");
        }
        if (normalizedAmount.compareTo(payment.getAmount()) != 0
                || normalizedAmount.compareTo(caseEvent.getAmount()) != 0) {
            throw new BadRequestException("PAYMENT_AMOUNT_MISMATCH", "Bank evidence amount must exactly match the payment and case");
        }
        if (!normalizedCurrency.equals(CurrencyGuard.canonical(payment.getCurrency()))
                || !normalizedCurrency.equals(CurrencyGuard.canonical(caseEvent.getCurrency()))) {
            throw new BadRequestException("PAYMENT_CURRENCY_MISMATCH", "Bank evidence currency must exactly match the payment and case");
        }
        if (!"BANK_TRANSFER".equals(payment.getPaymentMethod())
                || payment.getDestinationReference() == null
                || !payment.getDestinationReference().equals(request.counterpartyReference())) {
            throw new BadRequestException("BANK_COUNTERPARTY_MISMATCH", "Bank counterparty must match the scheduled bank-transfer destination");
        }
        validateOutcomeTransition(payment.getStatus(), request.outcome());

        var evidence = new PayrollPaymentEvent();
        evidence.setPayment(payment);
        evidence.setSourceType("BANK");
        evidence.setSourceKey(request.idempotencyKey());
        evidence.setActorId(actorId);
        evidence.setOutcome(request.outcome());
        evidence.setAmount(normalizedAmount);
        evidence.setCurrency(normalizedCurrency);
        evidence.setProviderReference(request.transactionReference().trim());
        evidence.setOccurredAt(occurredAt);
        evidence.setStatus("APPLIED");
        evidence.setResolvesEvent(caseEvent);
        evidence.setPayloadJson(json.writeValueAsString(payload));
        evidence.setVerificationJson(json.writeValueAsString(verification(request, actorId)));
        events.saveAndFlush(evidence);

        if (!"SUCCEEDED".equals(payment.getStatus())) {
            var bankOutcome = new VerifiedPayrollPaymentEvent(
                    request.transactionReference(), paymentId, request.outcome(), normalizedAmount,
                    normalizedCurrency, request.transactionReference().trim(), occurredAt);
            outcomes.apply(payment, run, bankOutcome, actorId, request.transactionReference());
        }

        return new PayrollPaymentEventView(
                evidence.getId(), evidence.getSourceKey(), evidence.getStatus(), null,
                com.company.logicstic.dto.payroll.PayrollPaymentView.from(payment));
    }

    private PayrollPaymentEventView replay(
            PayrollPaymentEvent existing,
            UUID paymentId,
            UUID actorId,
            Map<String, Object> expectedPayload,
            com.company.logicstic.entity.PayrollPayment payment) {
        var sameRequest = paymentId.equals(existing.getPayment().getId())
                && actorId.equals(existing.getActorId())
                && json.readTree(existing.getPayloadJson()).equals(json.readTree(json.writeValueAsString(expectedPayload)));
        if (!sameRequest) {
            throw new BadRequestException("BANK_RECONCILIATION_IDEMPOTENCY_CONFLICT", "Idempotency key already has different reconciliation inputs");
        }
        return new PayrollPaymentEventView(existing.getId(), existing.getSourceKey(), existing.getStatus(),
                existing.getReason(), com.company.logicstic.dto.payroll.PayrollPaymentView.from(payment));
    }

    private void validate(ManualPayrollBankReconciliationRequest request, UUID actorId) {
        if (request == null || actorId == null || !employees.existsById(actorId)) {
            throw new ForbiddenException("Authenticated payroll employee is required for bank reconciliation");
        }
        if (request.idempotencyKey().isBlank() || request.bankSource().isBlank()
                || request.transactionReference().isBlank() || request.counterpartyReference().isBlank()
                || request.evidenceReference().isBlank() || request.reason().isBlank()) {
            throw new BadRequestException("BANK_RECONCILIATION_INPUT_INVALID", "Bank source, transaction, counterparty, evidence and reason are required");
        }
        if (request.amount().signum() < 0) {
            throw new BadRequestException("PAYMENT_AMOUNT_INVALID", "Bank evidence amount cannot be negative");
        }
        normalizeAmount(request.amount());
        CurrencyGuard.canonical(request.currency());
        if (!"SUCCEEDED".equals(request.outcome()) && !"FAILED".equals(request.outcome())) {
            throw new BadRequestException("BANK_RECONCILIATION_OUTCOME_INVALID", "Bank outcome must be SUCCEEDED or FAILED");
        }
    }

    private void validateOutcomeTransition(String paymentStatus, String outcome) {
        if ("SUCCEEDED".equals(paymentStatus)) {
            if (!"SUCCEEDED".equals(outcome)) {
                throw new BadRequestException("PAYMENT_SUCCESS_HISTORY_IMMUTABLE", "A successful payment cannot be downgraded by bank evidence");
            }
            return;
        }
        if (!java.util.Set.of("SCHEDULED", "SUBMITTED", "PROCESSING", "FAILED").contains(paymentStatus)) {
            throw new BadRequestException("PAYMENT_RECONCILIATION_STATE_INVALID", "Payment state cannot be resolved by manual bank evidence");
        }
        if ("FAILED".equals(paymentStatus) && "FAILED".equals(outcome)) {
            return;
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("PAYMENT_AMOUNT_REQUIRED", "Explicit bank evidence amount is required");
        }
        try {
            var normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new ArithmeticException("precision");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BadRequestException("PAYMENT_AMOUNT_INVALID", "Amount must fit NUMERIC(19,4) without rounding");
        }
    }

    private OffsetDateTime normalizeTimestamp(OffsetDateTime value) {
        if (value == null) {
            throw new BadRequestException("BANK_RECONCILIATION_TIME_REQUIRED", "Bank transaction time is required");
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private Map<String, Object> payload(
            ManualPayrollBankReconciliationRequest request,
            BigDecimal amount,
            String currency,
            OffsetDateTime occurredAt) {
        var value = new LinkedHashMap<String, Object>();
        value.put("idempotencyKey", request.idempotencyKey());
        value.put("caseEventId", request.caseEventId());
        value.put("bankSource", request.bankSource().trim());
        value.put("transactionReference", request.transactionReference().trim());
        value.put("amount", amount);
        value.put("currency", currency);
        value.put("outcome", request.outcome());
        value.put("counterpartyReference", request.counterpartyReference().trim());
        value.put("evidenceReference", request.evidenceReference().trim());
        value.put("reason", request.reason().trim());
        value.put("occurredAt", occurredAt);
        return value;
    }

    private Map<String, Object> verification(
            ManualPayrollBankReconciliationRequest request,
            UUID actorId) {
        var value = new LinkedHashMap<String, Object>();
        value.put("idempotencyKey", request.idempotencyKey());
        value.put("bankSource", request.bankSource().trim());
        value.put("transactionReference", request.transactionReference().trim());
        value.put("counterpartyReference", request.counterpartyReference().trim());
        value.put("evidenceReference", request.evidenceReference().trim());
        value.put("reason", request.reason().trim());
        value.put("actorId", actorId);
        value.put("attestationType", "MANUAL_BANK_RECONCILIATION");
        return value;
    }
}
