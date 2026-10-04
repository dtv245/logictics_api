package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.service.cost.ExpenseApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpenseApprovalServiceTest {
    final ExpenseRepository expenses = mock(ExpenseRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final ShipmentCostEngine projector = mock(ShipmentCostEngine.class);
    final ExpenseApprovalService service = new ExpenseApprovalService(expenses, employees, projector);
    Expense expense;
    Employee actor;

    @BeforeEach void setup() {
        actor = new Employee(); actor.setId(UUID.randomUUID());
        expense = new Expense(); expense.setId(UUID.randomUUID()); expense.setStatus("PENDING_APPROVAL");
        expense.setAmountAmount(new BigDecimal("25.00")); expense.setAmountCurrency("usd");
        expense.setExpenseDate(OffsetDateTime.now());
        when(employees.findByEmail("accountant@example.test")).thenReturn(Optional.of(actor));
        when(expenses.findByIdForUpdate(expense.getId())).thenReturn(Optional.of(expense));
    }

    @Test void approvesAndProjectsAttributedExpense() {
        Load load = new Load(); load.setId(UUID.randomUUID()); expense.setLoad(load);
        ShipmentCost cost = new ShipmentCost(); cost.setId(UUID.randomUUID());
        when(projector.projectApprovedExpense(expense)).thenReturn(cost);
        var result = service.approve(expense.getId(), "accountant@example.test");
        assertEquals("APPROVED", result.status()); assertEquals("USD", expense.getAmountCurrency());
        assertEquals(actor.getId().toString(), result.approvedBy()); assertNotNull(result.approvedAt());
        assertEquals(cost.getId(), result.shipmentCostId()); verify(expenses).save(expense); verify(projector).projectApprovedExpense(expense);
    }
    @Test void repeatApprovalPreservesOriginalAuditAndDoesNotSaveAgain() {
        expense.setStatus("APPROVED"); expense.setApprovedById("original");
        var at = OffsetDateTime.now().minusDays(1); expense.setApprovedAt(at);
        var result = service.approve(expense.getId(), "accountant@example.test");
        assertEquals(at, result.approvedAt()); assertEquals("original", result.approvedBy());
        verify(expenses, never()).save(any()); verifyNoInteractions(projector);
    }
    @Test void rejectsRejectedExpenseWithoutProjection() {
        expense.setStatus("REJECTED");
        assertThrows(BadRequestException.class, () -> service.approve(expense.getId(), "accountant@example.test"));
        verifyNoInteractions(projector); verify(expenses, never()).save(any());
    }
    @Test void rejectsInvalidAmountBeforeApproval() {
        expense.setAmountAmount(new BigDecimal("-1"));
        assertThrows(BadRequestException.class, () -> service.approve(expense.getId(), "accountant@example.test"));
        assertEquals("PENDING_APPROVAL", expense.getStatus()); verifyNoInteractions(projector);
    }
}
