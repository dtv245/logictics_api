package com.company.logicstic.reporting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An invoice that bears revenue, with the money actually received against it left out on purpose.
 *
 * <p>Payments are read by a separate query and matched here by invoice id, rather than joined. The
 * {@code Invoice} entity maps no {@code payments} collection — {@code Payment.invoice} is the only
 * side of that relationship that exists — and adding one would mean editing another feature's
 * entity to serve a report.
 *
 * <p>{@code status} travels with the row so the service can compute the status-driven view of what
 * is outstanding alongside the payment-driven one and compare them. The two disagreeing is a
 * finding, not an error: it is reported as {@code invoicesWithStatusPaymentMismatch}.
 *
 * @param invoiceId invoice identifier
 * @param customerId the customer billed, or {@code null} when the invoice names none
 * @param totalAmount the full invoice amount
 * @param dueDate when payment was due, or {@code null} when the invoice has no due date. A null is
 *     never treated as "not yet due" — an invoice nobody has shown to be current does not become
 *     current by omission
 * @param status the stored status word, free text and not normalised here
 */
public record InvoiceBalance(
    UUID invoiceId,
    UUID customerId,
    BigDecimal totalAmount,
    OffsetDateTime dueDate,
    String status) {}
