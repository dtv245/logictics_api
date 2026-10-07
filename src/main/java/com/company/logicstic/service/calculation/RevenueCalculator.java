package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.common.enums.InvoiceStatus;
import com.company.logicstic.dto.report.LoadRevenueSummary;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Payment;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RevenueCalculator {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final LoadRepository loadRepository;
    private final InvoiceReconciliationService invoiceReconciliationService;
    private final CustomerBalanceCalculator customerBalanceCalculator;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public LoadRevenueSummary calculateLoadRevenue(UUID loadId, String reportCurrency) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        List<Invoice> documents = invoiceRepository.findAllByLoadId(loadId);
        if (documents.isEmpty()) {
            String fallbackCurrency = reportCurrency != null ? reportCurrency : "USD";
            return new LoadRevenueSummary(
                    loadId,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    fallbackCurrency,
                    "UNAVAILABLE_UNVERIFIED_LEGACY_DISTANCE",
                    null
            );
        }

        String currency = CurrencyGuard.normalize(reportCurrency != null ? reportCurrency : documents.getFirst().getSubtotalCurrency());
        BigDecimal subtotal = BigDecimal.ZERO,tax=BigDecimal.ZERO,total=BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        for(Invoice invoice:documents) {
        invoiceReconciliationService.reconcile(invoice);
        CurrencyGuard.requireSameCurrency(currency,invoice.getSubtotalCurrency());
        CurrencyGuard.requireSameCurrency(currency,invoice.getTotalCurrency());
        CurrencyGuard.requireSameCurrency(currency,invoice.getTaxTotalCurrency());
        boolean validInvoice=InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue();
        if(validInvoice) {
            var sign=BigDecimal.valueOf(invoice.economicSign());
            subtotal=subtotal.add(invoice.getSubtotalAmount().multiply(sign));tax=tax.add(invoice.getTaxTotalAmount().multiply(sign));
            total=total.add(invoice.getTotalAmount().multiply(sign));
        }
        List<Payment> payments = validInvoice ? paymentRepository.findByInvoiceId(invoice.getId()) : List.of();
        if (payments != null) {
            for (Payment payment : payments) {
                if (("completed".equalsIgnoreCase(payment.getStatus()) || "paid".equalsIgnoreCase(payment.getStatus())
                        || "succeeded".equalsIgnoreCase(payment.getStatus()) || "settled".equalsIgnoreCase(payment.getStatus()))
                        && payment.getAmountAmount() != null) {
                    CurrencyGuard.requireSameCurrency(currency, payment.getAmountCurrency());
                    paidAmount = paidAmount.add(payment.getAmountAmount());
                }
            }
        }
        }

        BigDecimal openBalance = total.subtract(paidAmount);
        BigDecimal roundedSubtotal = rounding.money(subtotal, currency, REPORT);
        BigDecimal roundedTax = rounding.money(tax, currency, REPORT);
        BigDecimal roundedTotal = rounding.money(total, currency, REPORT);
        BigDecimal roundedPaid = rounding.money(paidAmount, currency, REPORT);
        BigDecimal roundedOpenBalance = rounding.money(openBalance, currency, REPORT);

        return new LoadRevenueSummary(
                loadId,
                roundedSubtotal,
                roundedTax,
                roundedTotal,
                roundedPaid,
                roundedOpenBalance,
                currency,
                "UNAVAILABLE_UNVERIFIED_LEGACY_DISTANCE",
                null
        );
    }

    @Transactional(readOnly = true)
    public com.company.logicstic.dto.report.CustomerBalanceReport calculateCustomerBalance(UUID customerId, String reportCurrency) {
        return customerBalanceCalculator.calculate(customerId, reportCurrency);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateRevenueForPeriod(OffsetDateTime from, OffsetDateTime to, String reportCurrency) {
        String targetCurrency = CurrencyGuard.normalize(reportCurrency != null ? reportCurrency : "USD");
        List<Invoice> invoices = invoiceRepository.findByPeriod(from, to);

        BigDecimal revenueSum = BigDecimal.ZERO;
        for (Invoice inv : invoices) {
            InvoiceStatus status = InvoiceStatus.fromString(inv.getStatus());
            if (status.countsAsRevenue() && inv.getSubtotalAmount() != null) {
                CurrencyGuard.requireSameCurrency(targetCurrency, inv.getSubtotalCurrency());
                revenueSum = revenueSum.add(inv.getSubtotalAmount().multiply(BigDecimal.valueOf(inv.economicSign())));
            }
        }
        return rounding.money(revenueSum, targetCurrency, REPORT);
    }
}
