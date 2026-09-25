package com.company.logicstic.reporting;

import com.company.logicstic.finance.invoice.Invoice;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate reads over invoices, for reporting only.
 *
 * <p>This is not {@code InvoiceRepository} and does not replace it. It exists because reporting
 * needs sums and groupings that a CRUD repository has no reason to expose, and because the
 * architecture rule that keeps one feature out of another's data layer is satisfied by reporting
 * owning its own repository rather than by reaching into the invoice feature's.
 *
 * <p>Every status predicate is written {@code LOWER(i.status) IN :statuses} against lowercase
 * values supplied by the service. That is not decoration: {@code invoices.status} is plain text
 * that this codebase already writes in two casings — {@code DataSeeder} writes {@code Draft} and
 * {@code Issued} while {@code InvoiceDispatchStatus.dbValue()} writes {@code draft} and {@code
 * issued} — so a case-sensitive comparison would silently drop real rows and understate revenue.
 * The service never passes a literal; it passes {@link
 * com.company.logicstic.finance.invoice.InvoiceStatus#revenueBearingLowercase()}.
 *
 * <p>Currency is filtered inside the query rather than after it, so the count of rows excluded for
 * carrying another currency is a real row count and not a guess. Summing amounts across currencies
 * would produce a number with no unit.
 *
 * <p>No {@code @Transactional} — the transaction boundary is the service.
 */
public interface ReportingInvoiceRepository extends JpaRepository<Invoice, UUID> {

  /** Revenue per calendar month, UTC, over the window. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.InvoiceMonthlyRevenue(
          YEAR(i.createdAt), MONTH(i.createdAt), SUM(i.totalAmount), COUNT(i))
      FROM Invoice i
      WHERE i.totalCurrency = :currency
        AND LOWER(i.status) IN :statuses
        AND i.createdAt >= :from AND i.createdAt < :to
      GROUP BY YEAR(i.createdAt), MONTH(i.createdAt)
      """)
  List<InvoiceMonthlyRevenue> findMonthlyRevenue(
      @Param("currency") String currency,
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /**
   * Revenue per customer over the window, largest first, with no limit.
   *
   * <p>Deliberately unlimited: the caller applies its display limit afterwards, once concentration
   * shares and the Herfindahl index have been computed from the complete list. Invoices naming no
   * customer are excluded here — an unnamed customer is not a customer — and counted separately by
   * {@link #countRevenueBearingWithoutCustomer}.
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.InvoiceCustomerRevenue(
          i.customer.id, i.customer.name, SUM(i.totalAmount), COUNT(i))
      FROM Invoice i
      WHERE i.totalCurrency = :currency
        AND LOWER(i.status) IN :statuses
        AND i.createdAt >= :from AND i.createdAt < :to
        AND i.customer IS NOT NULL
      GROUP BY i.customer.id, i.customer.name
      ORDER BY SUM(i.totalAmount) DESC
      """)
  List<InvoiceCustomerRevenue> findRevenueByCustomer(
      @Param("currency") String currency,
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /**
   * Every revenue-bearing invoice, with no date filter.
   *
   * <p>Receivables is a position, not a flow: an invoice issued two years ago and never paid is
   * still outstanding today, so narrowing by creation date would hide exactly the debt a
   * receivables report exists to find. The ageing is computed against the caller's {@code asOf}
   * instead.
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.InvoiceBalance(
          i.id, i.customer.id, i.totalAmount, i.dueDate, i.status)
      FROM Invoice i
      WHERE i.totalCurrency = :currency
        AND LOWER(i.status) IN :statuses
      """)
  List<InvoiceBalance> findRevenueBearingBalances(
      @Param("currency") String currency, @Param("statuses") Collection<String> statuses);

  /** Revenue-bearing invoices in the window that name no customer. */
  @Query(
      """
      SELECT COUNT(i) FROM Invoice i
      WHERE i.totalCurrency = :currency
        AND LOWER(i.status) IN :statuses
        AND i.createdAt >= :from AND i.createdAt < :to
        AND i.customer IS NULL
      """)
  long countRevenueBearingWithoutCustomer(
      @Param("currency") String currency,
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /**
   * Invoices in the window carrying a currency other than the requested one.
   *
   * <p>No status filter: this counts what the currency predicate removed, so it must see everything
   * the currency predicate would otherwise have removed, including drafts.
   */
  @Query(
      """
      SELECT COUNT(i) FROM Invoice i
      WHERE i.createdAt >= :from AND i.createdAt < :to
        AND i.totalCurrency <> :currency
      """)
  long countInOtherCurrency(
      @Param("currency") String currency,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /**
   * Distinct currencies present in the window, so a caller can see what it should have asked for.
   */
  @Query(
      """
      SELECT DISTINCT i.totalCurrency FROM Invoice i
      WHERE i.createdAt >= :from AND i.createdAt < :to
      ORDER BY i.totalCurrency
      """)
  List<String> findDistinctCurrencies(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /**
   * The invoice {@code type} distribution, as stored.
   *
   * <p>Reported rather than filtered on. Seeded invoices pick {@code standard} or {@code
   * credit_note} at random with always-positive amounts, so the type column does not tell revenue
   * from a credit and a filter built on it would delete roughly half of all revenue at random.
   * Credit notes are therefore summed in as stored — there is no sign convention in this schema to
   * net them by — and their presence is disclosed here instead of being silently normalised away.
   */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(i.type, COUNT(i))
      FROM Invoice i
      WHERE i.createdAt >= :from AND i.createdAt < :to
      GROUP BY i.type
      ORDER BY COUNT(i) DESC
      """)
  List<GroupCount> groupByType(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** The stored status distribution, unnormalised, so unrecognised values stay visible. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(i.status, COUNT(i))
      FROM Invoice i
      WHERE i.createdAt >= :from AND i.createdAt < :to
      GROUP BY i.status
      ORDER BY COUNT(i) DESC
      """)
  List<GroupCount> groupByStatus(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /** Total invoice amount in the window and currency, without an invoice row count. */
  @Query(
      """
      SELECT SUM(i.totalAmount) FROM Invoice i
      WHERE i.totalCurrency = :currency
        AND LOWER(i.status) IN :statuses
        AND i.createdAt >= :from AND i.createdAt < :to
      """)
  BigDecimal sumRevenue(
      @Param("currency") String currency,
      @Param("statuses") Collection<String> statuses,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);
}
