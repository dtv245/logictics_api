package com.company.logicstic.reporting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One expense row, unaggregated, because its cost category is decided in Java.
 *
 * <p>Summing by category in SQL is not possible here: {@code expenses} has no category vocabulary,
 * only three free-text columns ({@code type}, {@code category}, {@code truck_expense_category})
 * whose contents this deployment has never observed — there is no expense seeder and no test that
 * constructs an {@code Expense}. {@link CostCategory#classify} matches keywords against all three,
 * which is a rule that belongs in testable Java rather than in a {@code CASE} expression.
 *
 * <p>Both vehicle foreign keys are carried, and neither is used to join. {@code expenses} has two
 * columns pointing at {@code trucks} — an artefact of two expense shapes merged into one table —
 * and a row can have both set. A query joining on either, or grouping by {@code COALESCE(truck_id,
 * truck_expense_truck_id)}, risks the same expense being counted twice. Carrying both into Java
 * lets the service count each row exactly once and report the rows where the two disagree.
 *
 * @param expenseId expense identifier
 * @param expenseDate when the cost was incurred. Carried so the monthly financials can bucket cost
 *     by month in Java, alongside the revenue that came back already grouped by the database. Two
 *     sources grouped in two different places have to meet on the same key, and the only key both
 *     can produce is a calendar month
 * @param type the {@code type} column, free text
 * @param category the {@code category} column, free text
 * @param truckExpenseCategory the {@code truck_expense_category} column, free text
 * @param amount the expense amount, in the requested currency
 * @param truckId first vehicle foreign key, or {@code null}
 * @param truckExpenseTruckId second vehicle foreign key, or {@code null}
 * @param status the stored status word, carried so the distribution can be reported
 */
public record ExpenseLine(
    UUID expenseId,
    OffsetDateTime expenseDate,
    String type,
    String category,
    String truckExpenseCategory,
    BigDecimal amount,
    UUID truckId,
    UUID truckExpenseTruckId,
    String status) {}
