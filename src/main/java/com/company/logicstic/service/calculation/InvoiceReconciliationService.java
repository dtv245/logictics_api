package com.company.logicstic.service.calculation;

import java.math.BigDecimal;

import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import lombok.RequiredArgsConstructor;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.InvoiceLineItem;
import com.company.logicstic.exception.InvoiceReconciliationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvoiceReconciliationService {
    private final FinancialRoundingPolicy rounding;

    public void reconcile(Invoice invoice) {
        if (invoice == null) {
            return;
        }

        BigDecimal recordedSubtotal = invoice.getSubtotalAmount() == null ? BigDecimal.ZERO : invoice.getSubtotalAmount();
        String currency = CurrencyGuard.normalize(invoice.getSubtotalCurrency());

        BigDecimal linesSum = BigDecimal.ZERO;
        if (invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()) {
            for (InvoiceLineItem item : invoice.getLineItems()) {
                if (item.getAmountAmount() != null) {
                    CurrencyGuard.requireSameCurrency(currency, item.getAmountCurrency());
                    linesSum = linesSum.add(item.getAmountAmount());
                }
            }
        }

        BigDecimal roundedRecorded = rounding.money(recordedSubtotal, currency, FinancialRoundingPolicy.Boundary.INVOICE);
        BigDecimal roundedLines = rounding.money(linesSum, currency, FinancialRoundingPolicy.Boundary.INVOICE);

        if (roundedRecorded.compareTo(roundedLines) != 0) {
            throw new InvoiceReconciliationException(invoice.getId(), roundedRecorded, roundedLines);
        }
    }
}
