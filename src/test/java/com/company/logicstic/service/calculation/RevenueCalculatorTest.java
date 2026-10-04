package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.dto.report.CustomerBalanceReport;
import com.company.logicstic.dto.report.LoadRevenueSummary;
import com.company.logicstic.entity.Customer;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.InvoiceLineItem;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Payment;
import com.company.logicstic.repository.CustomerRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueCalculatorTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private LoadRepository loadRepository;
    @Mock
    private CustomerRepository customerRepository;
    private RevenueCalculator revenueCalculator;

    private UUID loadId;
    private Load load;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        var rounding = TestRoundingPolicies.standard();
        revenueCalculator = new RevenueCalculator(invoiceRepository, paymentRepository, loadRepository,
                new InvoiceReconciliationService(rounding),
                new CustomerBalanceCalculator(customerRepository, invoiceRepository, paymentRepository, rounding), rounding);
        loadId = UUID.randomUUID();
        load = new Load();
        load.setId(loadId);
        load.setDistance(500.0);

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus("ISSUED");
        invoice.setSubtotalAmount(new BigDecimal("2000.00"));
        invoice.setSubtotalCurrency("USD");
        invoice.setTaxTotalAmount(new BigDecimal("200.00"));
        invoice.setTaxTotalCurrency("USD");
        invoice.setTotalAmount(new BigDecimal("2200.00"));
        invoice.setTotalCurrency("USD");

        InvoiceLineItem item = new InvoiceLineItem();
        item.setAmountAmount(new BigDecimal("2000.00"));
        item.setAmountCurrency("USD");
        invoice.setLineItems(List.of(item));
    }

    @Test
    @DisplayName("Calculate revenue/balance without inferring RPM from legacy distance")
    void testCalculateLoadRevenue() {
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(invoiceRepository.findAllByLoadId(loadId)).thenReturn(List.of(invoice));

        Payment payment = new Payment();
        payment.setStatus("completed");
        payment.setAmountAmount(new BigDecimal("1200.00"));
        payment.setAmountCurrency("USD");
        when(paymentRepository.findByInvoiceId(invoice.getId())).thenReturn(List.of(payment));

        LoadRevenueSummary summary = revenueCalculator.calculateLoadRevenue(loadId, "USD");

        assertNotNull(summary);
        assertEquals(new BigDecimal("2000.00"), summary.subtotalRevenue());
        assertEquals(new BigDecimal("200.00"), summary.taxAmount());
        assertEquals(new BigDecimal("2200.00"), summary.totalInvoiceAmount());
        assertEquals(new BigDecimal("1200.00"), summary.paidAmount());
        assertEquals(new BigDecimal("1000.00"), summary.openBalance());
        assertEquals("USD", summary.currency());
        assertEquals("UNAVAILABLE_UNVERIFIED_LEGACY_DISTANCE", summary.distanceBasis());
        org.junit.jupiter.api.Assertions.assertNull(summary.revenuePerMile());
    }

    @Test
    @DisplayName("Calculate customer open balance across multiple invoices")
    void testCalculateCustomerBalance() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsById(customerId)).thenReturn(true);
        when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of(invoice));

        Payment payment = new Payment();
        payment.setStatus("completed");
        payment.setAmountAmount(new BigDecimal("1000.00"));
        payment.setAmountCurrency("USD");
        when(paymentRepository.findByInvoiceId(invoice.getId())).thenReturn(List.of(payment));

        CustomerBalanceReport report = revenueCalculator.calculateCustomerBalance(customerId, "USD");

        assertNotNull(report);
        assertEquals(new BigDecimal("2200.00"), report.totalInvoiced());
        assertEquals(new BigDecimal("1000.00"), report.totalPaid());
        assertEquals(new BigDecimal("1200.00"), report.openBalance());
        assertEquals(1, report.invoiceCount());
    }
}
