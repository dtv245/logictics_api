package com.company.logicstic.reporting;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One customer's revenue over the window.
 *
 * <p>The query behind this returns <em>every</em> customer with revenue, with no limit. The limit
 * the caller asked for is applied afterwards, in the service, only to decide how many rows to
 * display. Concentration shares and the Herfindahl index are computed from this complete list,
 * because both are ratios against a total: computed from a truncated list they would be measured
 * against a truncated denominator and would overstate concentration, which is the one thing the
 * endpoint exists to measure. The complete list is also what keeps the figures still as the display
 * limit moves — {@code limit} must not change the number printed beside the list.
 *
 * @param customerId customer identifier
 * @param customerName customer display name
 * @param revenue total invoice amount in the requested currency
 * @param invoiceCount how many invoices contributed
 */
public record InvoiceCustomerRevenue(
    UUID customerId, String customerName, BigDecimal revenue, long invoiceCount) {}
