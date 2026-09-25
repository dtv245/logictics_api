package com.company.logicstic.reporting;

import com.company.logicstic.fleet.truck.Expense;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads over expenses for reporting.
 *
 * <p>Rows come back unaggregated because their cost category cannot be decided in SQL — {@code
 * expenses} has three free-text classification columns and no vocabulary — so the service groups
 * them in Java with {@link CostCategory#classify}. See {@link ExpenseLine} for why both vehicle
 * foreign keys travel with every row.
 *
 * <p><strong>No status predicate.</strong> {@code expenses.status} is free text whose values this
 * deployment has never observed: there is no expense seeder and no test that builds an expense, so
 * a filter naming approved-or-paid would be a guess about a vocabulary nobody has written down.
 * Every row in the currency and window is therefore summed, and the status distribution is returned
 * by {@link #groupByStatus} so a reader can see whether rejected or draft expenses are inflating
 * the total and ask for a filter deliberately, rather than having one guessed for them.
 */
public interface ReportingExpenseRepository extends JpaRepository<Expense, UUID> {

  /** Every expense in the currency and window, in the requested currency. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.ExpenseLine(
          e.id, e.expenseDate, e.type, e.category, e.truckExpenseCategory, e.amountAmount,
          e.truck.id, e.truckExpenseTruck.id, e.status)
      FROM Expense e
      WHERE e.amountCurrency = :currency
        AND e.expenseDate >= :from AND e.expenseDate < :to
      """)
  List<ExpenseLine> findLines(
      @Param("currency") String currency,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** Expenses in the window carrying a currency other than the requested one. */
  @Query(
      """
      SELECT COUNT(e) FROM Expense e
      WHERE e.expenseDate >= :from AND e.expenseDate < :to
        AND e.amountCurrency <> :currency
      """)
  long countInOtherCurrency(
      @Param("currency") String currency,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  /** The stored status distribution, unnormalised, so unrecognised values stay visible. */
  @Query(
      """
      SELECT new com.company.logicstic.reporting.GroupCount(e.status, COUNT(e))
      FROM Expense e
      WHERE e.expenseDate >= :from AND e.expenseDate < :to
      GROUP BY e.status
      ORDER BY COUNT(e) DESC
      """)
  List<GroupCount> groupByStatus(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /**
   * Distinct currencies present among expenses in the window, so a caller can see what is there.
   *
   * <p>{@code Collection} rather than a single value is what tells a reader that an unfiltered sum
   * across this table would be meaningless.
   */
  @Query(
      """
      SELECT DISTINCT e.amountCurrency FROM Expense e
      WHERE e.expenseDate >= :from AND e.expenseDate < :to
      ORDER BY e.amountCurrency
      """)
  Collection<String> findDistinctCurrencies(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
