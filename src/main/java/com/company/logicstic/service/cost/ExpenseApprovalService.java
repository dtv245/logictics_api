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

    public record ApprovalResult(UUID expenseId, String status, OffsetDateTime approvedAt,
                                 String approvedBy, UUID shipmentCostId) {}
}
