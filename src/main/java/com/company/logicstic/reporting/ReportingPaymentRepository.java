package com.company.logicstic.reporting;

import com.company.logicstic.finance.payment.Payment;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate reads over payments, for reporting only.
 *
 * <p>The status predicate takes both spellings of a settled payment. {@code completed} and {@code
 * paid} are both real in this codebase — {@code DataSeeder} writes one and the functional tests
 * create the other, and both are asserted against rows that actually exist — so treating either as
 * the only truth would silently exclude real money.
 *
 * <p>Grouped by invoice id rather than joined to invoices, because {@code Invoice} maps no {@code
 * payments} collection and adding one would mean editing the invoice feature's entity for a
 * report's convenience. The result is small — one row per invoice that has ever been paid — so the
 * join happens in memory.
 */
public interface ReportingPaymentRepository extends JpaRepository<Payment, UUID> {

  /**
   * Settled amount per invoice, in the requested currency.
   *
   * <p>Payments with no invoice are excluded: money not tied to an invoice cannot reduce any
   * invoice's outstanding balance. They are counted by {@link #countUnapplied} so the exclusion is
   * visible.
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.InvoicePayments(p.invoice.id, SUM(p.amountAmount))
      FROM Payment p
      WHERE p.amountCurrency = :currency
        AND LOWER(p.status) IN :statuses
        AND p.invoice IS NOT NULL
      GROUP BY p.invoice.id
      """)
  List<InvoicePayments> sumSettledByInvoice(
      @Param("currency") String currency, @Param("statuses") Collection<String> statuses);

  /** Settled payments carrying no invoice at all. */
  @Query(
      """
      SELECT COUNT(p) FROM Payment p
      WHERE p.amountCurrency = :currency
        AND LOWER(p.status) IN :statuses
        AND p.invoice IS NULL
      """)
  long countUnapplied(
      @Param("currency") String currency, @Param("statuses") Collection<String> statuses);

  /** Payments in the window carrying a currency other than the requested one. */
  @Query(
      """
      SELECT COUNT(p) FROM Payment p
      WHERE COALESCE(p.recordedAt, p.createdAt) >= :from
        AND COALESCE(p.recordedAt, p.createdAt) < :to
        AND p.amountCurrency <> :currency
      """)
  long countInOtherCurrency(
      @Param("currency") String currency,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);
}
