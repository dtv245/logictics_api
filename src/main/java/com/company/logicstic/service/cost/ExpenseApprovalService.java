package com.company.logicstic.service.cost;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** The approval and its ledger projection commit or roll back as one transaction. */
@Service @RequiredArgsConstructor
public class ExpenseApprovalService {
    private final ExpenseRepository expenses;
    private final EmployeeRepository employees;
    private final com.company.logicstic.service.calculation.ShipmentCostEngine projector;

    @Transactional
    public ApprovalResult approve(UUID expenseId, String authenticatedEmail) {
        var actor = employees.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ForbiddenException("Authenticated employee is required for approval"));
        Expense expense = expenses.findByIdForUpdate(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        String status = expense.getStatus() == null ? "" : expense.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(status)) {
            if (!Set.of("PENDING", "SUBMITTED", "PENDING_APPROVAL").contains(status)) {
                throw new BadRequestException("EXPENSE_APPROVAL_STATE_INVALID", "Only submitted/pending expenses can be approved");
            }
            if (expense.getAmountAmount() == null || expense.getAmountAmount().signum() < 0 || expense.getExpenseDate() == null) {
                throw new BadRequestException("EXPENSE_APPROVAL_INPUT_INVALID", "Amount and incurred date are required");
            }
            expense.setAmountCurrency(CurrencyGuard.normalize(expense.getAmountCurrency()));
            expense.setStatus("APPROVED");
            expense.setApprovedById(actor.getId().toString());
            expense.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            expenses.save(expense);
        }
        var cost = expense.getLoad() == null ? null : projector.projectApprovedExpense(expense);
        return new ApprovalResult(expense.getId(), expense.getStatus(), expense.getApprovedAt(),
                expense.getApprovedById(), cost == null ? null : cost.getId());
    }

    /**
     * Submitted -> REJECTED with a mandatory reason. Repeating the command preserves the
     * original rejection audit instead of overwriting it.
     */
    @Transactional
    public ApprovalResult reject(UUID expenseId, String reason, String authenticatedEmail) {
        var actor = employees.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ForbiddenException("Authenticated employee is required for rejection"));
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("EXPENSE_REJECTION_REASON_REQUIRED", "A rejection reason is required");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 500) {
            throw new BadRequestException("EXPENSE_REJECTION_REASON_INVALID",
                    "Rejection reason must be at most 500 characters");
        }
        Expense expense = expenses.findByIdForUpdate(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        String status = normalizeStatus(expense.getStatus());
        if ("REJECTED".equals(status)) {
            return new ApprovalResult(expense.getId(), expense.getStatus(), expense.getApprovedAt(),
                    expense.getApprovedById(), null);
        }
        if (!Set.of("PENDING", "SUBMITTED", "PENDING_APPROVAL").contains(status)) {
            throw new BadRequestException("EXPENSE_REJECTION_STATE_INVALID",
                    "Only submitted/pending expenses can be rejected");
        }
        expense.setStatus("REJECTED");
        expense.setRejectionReason(trimmedReason);
        expense.setRejectedAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        expense.setRejectedById(actor.getId().toString());
        expenses.save(expense);
        return new ApprovalResult(expense.getId(), expense.getStatus(), expense.getApprovedAt(),
                expense.getApprovedById(), null);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    public record ApprovalResult(UUID expenseId, String status, OffsetDateTime approvedAt,
                                 String approvedBy, UUID shipmentCostId) {}
}
