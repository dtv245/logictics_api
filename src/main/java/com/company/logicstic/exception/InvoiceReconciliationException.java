package com.company.logicstic.exception;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class InvoiceReconciliationException extends ApiException {

    public InvoiceReconciliationException(UUID invoiceId, BigDecimal invoiceSubtotal, BigDecimal linesSum) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVOICE_RECONCILIATION_FAILED",
                String.format("Invoice %s reconciliation failed: subtotal=%s does not equal sum of lines=%s", invoiceId, invoiceSubtotal, linesSum)
        );
    }
}
