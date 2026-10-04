package com.company.logicstic.service.payroll;

import com.company.logicstic.dto.payroll.NoPaymentRequiredRequest;
import com.company.logicstic.dto.payroll.PayrollRunView;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.PayrollPaymentEventRepository;
import com.company.logicstic.repository.PayrollPaymentRepository;
import com.company.logicstic.repository.PayrollRunItemRepository;
import com.company.logicstic.repository.PayrollRunRepository;
import com.company.logicstic.repository.PayslipRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollNoPaymentDispositionService {
    private static final Set<String> REASON_CODES = Set.of(
            "ZERO_NET_PAY", "FULLY_OFFSET_BY_DEDUCTIONS", "MANUAL_ADJUSTMENT_ZERO_BALANCE");
    private final PayrollRunItemRepository items;
    private final PayrollRunRepository runs;
    private final EmployeeRepository employees;
    private final PayrollPaymentRepository payments;
    private final PayrollPaymentEventRepository events;
    private final PayslipRepository payslips;
    private final PayrollRunCompletionService completion;
    private final PayrollCalculationService payroll;

    @Transactional
    public PayrollRunView markNoPaymentRequired(UUID itemId, NoPaymentRequiredRequest request, UUID actor) {
        if (request == null || actor == null || !employees.existsById(actor)) {
            throw new BadRequestException("PAYROLL_ACTOR_REQUIRED", "Persisted authenticated payroll actor and explicit disposition required");
        }
        var reasonCode = request.reasonCode() == null ? "" : request.reasonCode().trim();
        var reason = request.reason() == null ? "" : request.reason().trim();
        if (!REASON_CODES.contains(reasonCode) || reason.isBlank() || reason.length() > 1000) {
            throw new BadRequestException("PAYROLL_NO_PAYMENT_REASON_INVALID", "A supported reason code and audit explanation are required");
        }

        var runId = items.findPayrollRunId(itemId)
                .orElseThrow(() -> new BadRequestException("PAYROLL_ITEM_NOT_FOUND", "Payroll item not found"));
        var run = runs.findByIdForUpdate(runId)
                .orElseThrow(() -> new BadRequestException("PAYROLL_RUN_NOT_FOUND", "Payroll run not found"));
        var item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BadRequestException("PAYROLL_ITEM_NOT_FOUND", "Payroll item not found"));

        if ("NO_PAYMENT_REQUIRED".equals(item.getStatus())) {
            if (actor.equals(item.getNoPaymentRequiredBy())
                    && reasonCode.equals(item.getNoPaymentReasonCode())
                    && reason.equals(item.getNoPaymentReason())) {
                return payroll.get(runId);
            }
            throw new BadRequestException("PAYROLL_NO_PAYMENT_DISPOSITION_CONFLICT", "The item already has a different immutable disposition");
        }
        if (!Set.of("LOCKED", "PAYMENT_SCHEDULED").contains(run.getStatus())) {
            throw new BadRequestException("PAYROLL_NO_PAYMENT_RUN_NOT_LOCKED", "No-payment disposition requires locked payroll");
        }
        if (!Set.of("CALCULATED", "SCHEDULED").contains(item.getStatus())
                || item.getNetAmount() == null || item.getNetAmount().signum() != 0) {
            throw new BadRequestException("PAYROLL_NO_PAYMENT_NET_NOT_ZERO", "Only a locked, unscheduled payroll item with exactly zero net may be marked no-payment-required");
        }
        if (events.hasUnresolvedCase(itemId)) {
            throw new BadRequestException("PAYROLL_RECONCILIATION_REQUIRED", "Unresolved payment evidence blocks no-payment disposition");
        }
        if (!payments.findByItemIdOrderByAttemptNumberAsc(itemId).isEmpty()) {
            throw new BadRequestException("PAYROLL_PAYMENT_ATTEMPT_EXISTS", "An item with a payment attempt cannot be marked no-payment-required");
        }
        if (payslips.findByItemId(itemId).isEmpty()) {
            throw new BadRequestException("PAYROLL_NO_PAYMENT_PAYSLIP_REQUIRED", "The locked payroll payslip must exist before disposition");
        }

        item.setStatus("NO_PAYMENT_REQUIRED");
        item.setNoPaymentRequiredAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        item.setNoPaymentRequiredBy(actor);
        item.setNoPaymentReasonCode(reasonCode);
        item.setNoPaymentReason(reason);
        items.saveAndFlush(item);
        completion.completeIfReady(run, actor, "NO_PAYMENT_DISPOSITION");
        return payroll.get(runId);
    }
}
