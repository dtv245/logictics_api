package com.company.logicstic.service.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.repository.ShipmentCostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShipmentCostEngineTest {
    @Mock ShipmentCostRepository repository;
    ShipmentCostEngine engine;
    @BeforeEach void setup() { engine = new ShipmentCostEngine(repository, TestRoundingPolicies.standard()); }

    @Test void projectsApprovedAttributedExpenseOnce() {
        Expense expense = new Expense(); expense.setId(UUID.randomUUID()); expense.setStatus("APPROVED");
        expense.setAmountAmount(new BigDecimal("12.345")); expense.setAmountCurrency("USD"); expense.setCategory("fuel");
        Load load = new Load(); load.setId(UUID.randomUUID()); expense.setLoad(load);
        when(repository.findBySourceTypeAndSourceId("EXPENSE", expense.getId())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ShipmentCost cost = engine.projectApprovedExpense(expense);
        assertEquals("ACTUAL", cost.getCostBasis()); assertEquals("VERIFIED", cost.getStatus());
        assertEquals(new BigDecimal("12.35"), cost.getAmount()); assertEquals("USD", cost.getCurrency());
    }
    @Test void allocationUsesExplicitInputs() {
        assertEquals(new BigDecimal("31.25"), engine.allocateMaintenance(new BigDecimal("12.5"), new BigDecimal("2.5"), "USD"));
    }

    @Test void retryDoesNotRewriteFinalizedCostButRejectsAttributionDrift() {
        Expense expense = new Expense(); expense.setId(UUID.randomUUID()); expense.setStatus("APPROVED");
        expense.setAmountAmount(new BigDecimal("12.345")); expense.setAmountCurrency("USD"); expense.setCategory("fuel");
        Load load = new Load(); load.setId(UUID.randomUUID()); expense.setLoad(load);
        ShipmentCost cost = new ShipmentCost(); cost.setLoad(load); cost.setAmount(new BigDecimal("12.35"));
        cost.setCurrency("USD"); cost.setCategory("FUEL"); cost.setStatus("POSTED");
        when(repository.findBySourceTypeAndSourceId("EXPENSE", expense.getId())).thenReturn(Optional.of(cost));
        org.junit.jupiter.api.Assertions.assertSame(cost, engine.projectApprovedExpense(expense));
        Load another = new Load(); another.setId(UUID.randomUUID()); expense.setLoad(another);
        org.junit.jupiter.api.Assertions.assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> engine.projectApprovedExpense(expense));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
