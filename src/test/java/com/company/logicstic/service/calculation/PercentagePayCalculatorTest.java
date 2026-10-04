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

    Invoice document(UUID chain,String purpose,String amount,String status) {
        var i=invoice();i.setInvoicePurpose(purpose);i.setEconomicSign("CREDIT".equals(purpose)?-1:1);
        i.setBillingChainId(chain);i.setStatus(status);i.setSubtotalAmount(new BigDecimal(amount));
        i.setTotalAmount(i.getSubtotalAmount().add(i.getTaxTotalAmount()));i.getLineItems().getFirst().setAmountAmount(i.getSubtotalAmount());return i;
    }
    @Test void primaryBasisExcludesSupplementalCreditRebillAndTax() {
        UUID chain=UUID.randomUUID();var primary=document(chain,"PRIMARY","100","ISSUED");
        var p=policy();p.setRevenueBasis("PRIMARY_INVOICE_REVENUE");
        var result=calculator.calculateBillingChain(List.of(primary,document(chain,"SUPPLEMENTAL","20","ISSUED"),document(chain,"CREDIT","100","ISSUED"),document(chain,"REBILL","150","ISSUED")),p);
        assertEquals(new BigDecimal("25.00"),result.amount());assertEquals(1,result.documents().size());assertEquals(primary.getId(),result.documents().getFirst().invoiceId());
    }
    @Test void netBasisUsesPurposeSignAndOnlyEligibleDocumentStatuses() {
        UUID chain=UUID.randomUUID();var p=policy();p.setRevenueBasis("NET_ELIGIBLE_REVENUE");
        var result=calculator.calculateBillingChain(List.of(document(chain,"PRIMARY","100","ISSUED"),document(chain,"SUPPLEMENTAL","20","SENT"),
                document(chain,"CREDIT","100","PARTIALLY_PAID"),document(chain,"REBILL","150","PAID"),document(chain,"SUPPLEMENTAL","999","DRAFT")),p);
        assertEquals(new BigDecimal("170"),result.eligibleRevenue());assertEquals(new BigDecimal("42.50"),result.amount());assertEquals(4,result.documents().size());
        assertTrue(result.documents().stream().anyMatch(d->"CREDIT".equals(d.purpose()) && d.economicSign()==-1 && d.eligibleSubtotal().signum()>0));
    }
    @Test void newBasisRejectsUnclassifiedHistoryWrongChainCurrencyAndInvalidSign() {
        var p=policy();p.setRevenueBasis("NET_ELIGIBLE_REVENUE");assertThrows(BadRequestException.class,()->calculator.calculateBillingChain(List.of(invoice()),p));
        UUID chain=UUID.randomUUID();var primary=document(chain,"PRIMARY","100","ISSUED");
        assertThrows(BadRequestException.class,()->calculator.calculateBillingChain(List.of(primary,document(UUID.randomUUID(),"SUPPLEMENTAL","20","ISSUED")),p));
        var credit=document(chain,"CREDIT","10","ISSUED");credit.setEconomicSign(1);
        assertThrows(BadRequestException.class,()->calculator.calculateBillingChain(List.of(primary,credit),p));
        primary.setSubtotalCurrency("EUR");assertThrows(CurrencyMismatchException.class,()->calculator.calculateBillingChain(List.of(primary),p));
    }
    @Test void explicitLegacyBasisRetainsHistoricalBehaviorWithoutRelabeling() {
        var i=invoice();assertEquals(new BigDecimal("25.00"),calculator.calculateBillingChain(List.of(i),policy()).amount());assertNull(i.getInvoicePurpose());
    }
}
