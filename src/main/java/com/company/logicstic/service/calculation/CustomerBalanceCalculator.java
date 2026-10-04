package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.common.enums.InvoiceStatus;
import com.company.logicstic.dto.report.CustomerBalanceReport;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.Payment;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.CustomerRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerBalanceCalculator {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public CustomerBalanceReport calculate(UUID customerId, String reportCurrency) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        String currency = CurrencyGuard.normalize(reportCurrency != null ? reportCurrency : "USD");
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        int count = 0;

        for (Invoice invoice : invoiceRepository.findByCustomerId(customerId)) {
            if (!InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue()) continue;
            CurrencyGuard.requireSameCurrency(currency, invoice.getTotalCurrency());
            count++;
            if (invoice.getTotalAmount() != null) invoiced = invoiced.add(invoice.getTotalAmount().multiply(BigDecimal.valueOf(invoice.economicSign())));
            List<Payment> payments = paymentRepository.findByInvoiceId(invoice.getId());
            if (payments == null) continue;
            for (Payment payment : payments) {
                if (isSettled(payment.getStatus()) && payment.getAmountAmount() != null) {
                    CurrencyGuard.requireSameCurrency(currency, payment.getAmountCurrency());
                    paid = paid.add(payment.getAmountAmount());
                }
            }
        }

        return new CustomerBalanceReport(
                customerId,
                rounding.money(invoiced, currency, REPORT),
                rounding.money(paid, currency, REPORT),
                rounding.money(invoiced.subtract(paid), currency, REPORT),
                currency,
                count
        );
    }

    private boolean isSettled(String status) {
        return "completed".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status);
    }
}
