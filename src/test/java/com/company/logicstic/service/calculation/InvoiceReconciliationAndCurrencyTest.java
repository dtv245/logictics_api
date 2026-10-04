package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.InvoiceLineItem;
import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.exception.InvoiceReconciliationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceReconciliationAndCurrencyTest {

    private final InvoiceReconciliationService reconciliationService = new InvoiceReconciliationService(TestRoundingPolicies.standard());

    @Test
    @DisplayName("CurrencyGuard should pass when currencies match and throw CurrencyMismatchException when different")
    void testCurrencyGuard() {
        assertDoesNotThrow(() -> CurrencyGuard.requireSameCurrency("USD", "usd"));
        assertDoesNotThrow(() -> CurrencyGuard.requireSameCurrency("VND", "VND"));

        assertThrows(CurrencyMismatchException.class, () -> CurrencyGuard.requireSameCurrency("USD", "VND"));
        assertThrows(CurrencyMismatchException.class, () -> CurrencyGuard.requireSameCurrency("USD", "EUR"));
    }

    @Test
    @DisplayName("Reconciliation succeeds when subtotal matches sum of line items exactly")
    void testReconciliationSuccess() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setSubtotalAmount(new BigDecimal("1500.00"));
        invoice.setSubtotalCurrency("USD");

        InvoiceLineItem item1 = new InvoiceLineItem();
        item1.setAmountAmount(new BigDecimal("1000.00"));
        item1.setAmountCurrency("USD");

        InvoiceLineItem item2 = new InvoiceLineItem();
        item2.setAmountAmount(new BigDecimal("500.00"));
        item2.setAmountCurrency("USD");

        invoice.setLineItems(List.of(item1, item2));

        assertDoesNotThrow(() -> reconciliationService.reconcile(invoice));
    }

    @Test
    @DisplayName("Reconciliation fails with 0 tolerance when subtotal differs from sum of line items")
    void testReconciliationFailure() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setSubtotalAmount(new BigDecimal("1500.00"));
        invoice.setSubtotalCurrency("USD");

        InvoiceLineItem item1 = new InvoiceLineItem();
        item1.setAmountAmount(new BigDecimal("1000.00"));
        item1.setAmountCurrency("USD");

        InvoiceLineItem item2 = new InvoiceLineItem();
        item2.setAmountAmount(new BigDecimal("499.99")); // 0.01 mismatch!
        item2.setAmountCurrency("USD");

        invoice.setLineItems(List.of(item1, item2));

        assertThrows(InvoiceReconciliationException.class, () -> reconciliationService.reconcile(invoice));
    }
}
