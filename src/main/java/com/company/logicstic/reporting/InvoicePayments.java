package com.company.logicstic.reporting;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The settled amount recorded against one invoice.
 *
 * <p>Only payments whose own currency matches the requested one are summed, so an invoice billed in
 * USD and part-paid by a VND transfer shows the VND payment as absent rather than as a
 * like-for-like reduction. That overstates what is outstanding, which is the safe direction for a
 * receivables report, and the currency mismatch is visible in {@code DataCompleteness} rather than
 * being guessed away by an exchange rate nobody supplied.
 *
 * @param invoiceId the invoice the payments settle
 * @param paidAmount total settled amount, in the requested currency
 */
public record InvoicePayments(UUID invoiceId, BigDecimal paidAmount) {}
