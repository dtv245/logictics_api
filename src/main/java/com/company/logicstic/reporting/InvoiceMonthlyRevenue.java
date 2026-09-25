package com.company.logicstic.reporting;

import java.math.BigDecimal;

/**
 * One month of revenue, summed in the database rather than in memory.
 *
 * <p>Revenue is attributed to the month the invoice row was created, in UTC. This schema has no
 * {@code issued_at}: {@code sent_at} and {@code period_start} exist but are nullable and are not
 * populated by the seed data, so {@code created_at} is the only date every invoice actually has. A
 * report that bucketed on a nullable column would silently omit the rows where it was null.
 *
 * @param year the UTC calendar year
 * @param month the UTC calendar month, 1-based
 * @param revenue total invoice amount in the requested currency
 * @param invoiceCount how many invoices contributed
 */
public record InvoiceMonthlyRevenue(int year, int month, BigDecimal revenue, long invoiceCount) {}
