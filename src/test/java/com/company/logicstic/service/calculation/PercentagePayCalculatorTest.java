package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.*;
import com.company.logicstic.exception.*;
import com.company.logicstic.service.payroll.PercentagePayCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PercentagePayCalculatorTest {
    final PercentagePayCalculator calculator = new PercentagePayCalculator(TestRoundingPolicies.standard(),new InvoiceReconciliationService(TestRoundingPolicies.standard()));
    DriverPayPolicy policy() {
        var p = new DriverPayPolicy(); p.setId(UUID.randomUUID()); p.setPolicyVersion(2); p.setPayMethod("PERCENT_REVENUE");
        p.setRevenueBasis("INVOICE_SUBTOTAL"); p.setRevenuePercentage(new BigDecimal("0.25")); p.setCurrency("USD"); return p;
    }
    Invoice invoice() {
        var i = new Invoice(); i.setId(UUID.randomUUID()); i.setStatus("ISSUED"); i.setSubtotalAmount(new BigDecimal("100")); i.setSubtotalCurrency("USD");
        i.setTaxTotalAmount(new BigDecimal("10")); i.setTotalAmount(new BigDecimal("110"));
        var line = new InvoiceLineItem(); line.setAmountAmount(new BigDecimal("100")); line.setAmountCurrency("USD"); i.setLineItems(List.of(line)); return i;
    }
    @Test void appliesRatioToSubtotalExcludingTaxAndExplainsPolicyAndSource() {
        var i = invoice(); var p = policy(); var r = calculator.calculate(i,p);
        assertEquals(new BigDecimal("25.00"),r.amount()); assertEquals(new BigDecimal("100"),r.eligibleRevenue());
        assertEquals(i.getId(),r.invoiceId()); assertEquals(p.getId(),r.policyId()); assertEquals(2,r.policyVersion());
        assertEquals("INVOICE_SUBTOTAL",r.revenueBasis());
    }
    @Test void missingNonEligibleOrUnreconciledRevenueCannotBecomeZeroPay() {
        assertThrows(BadRequestException.class, () -> calculator.calculate(null,policy()));
        var i = invoice(); i.setStatus("DRAFT"); assertThrows(BadRequestException.class, () -> calculator.calculate(i,policy()));
        i.setStatus("ISSUED"); i.getLineItems().getFirst().setAmountAmount(new BigDecimal("99"));
        assertThrows(InvoiceReconciliationException.class, () -> calculator.calculate(i,policy()));
    }
    @Test void ratioBasisAndCurrencyAreValidatedAtCalculationBoundary() {
        var p = policy(); p.setRevenuePercentage(new BigDecimal("25")); assertThrows(BadRequestException.class, () -> calculator.calculate(invoice(),p));
        var wrongBasis = policy(); wrongBasis.setRevenueBasis("INVOICE_TOTAL");
        assertThrows(BadRequestException.class, () -> calculator.calculate(invoice(),wrongBasis));
        var i = invoice(); i.setSubtotalCurrency("VND"); assertThrows(CurrencyMismatchException.class, () -> calculator.calculate(i,policy()));
    }
}
