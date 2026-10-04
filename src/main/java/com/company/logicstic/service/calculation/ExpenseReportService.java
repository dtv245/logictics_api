package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.dto.report.ExpenseSummaryReport;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseReportService {
    private final ExpenseRepository expenseRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public ExpenseSummaryReport calculate(OffsetDateTime from, OffsetDateTime to, String reportCurrency) {
        return calculate(from, to, reportCurrency, false);
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryReport calculateOperatingCost(OffsetDateTime from, OffsetDateTime to, String reportCurrency) {
        return calculate(from, to, reportCurrency, true);
    }

    private ExpenseSummaryReport calculate(OffsetDateTime from, OffsetDateTime to, String reportCurrency, boolean excludeDriverCompensation) {
        String currency = CurrencyGuard.normalize(reportCurrency != null ? reportCurrency : "USD");
        List<Expense> expenses = expenseRepository.findApprovedExpenses(null, from, to);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new HashMap<>();
        int count = 0;
        for (Expense expense : expenses) {
            String category = expense.getCategory() == null ? "OTHER" : expense.getCategory().trim().toUpperCase(java.util.Locale.ROOT);
            if (excludeDriverCompensation && (category.contains("DRIVER") || category.contains("PAYROLL"))) continue;
            if (expense.getAmountAmount() == null) continue;
            CurrencyGuard.requireSameCurrency(currency, expense.getAmountCurrency());
            total = total.add(expense.getAmountAmount());
            byCategory.merge(category, expense.getAmountAmount(), BigDecimal::add);
            count++;
        }
        byCategory.replaceAll((category, amount) -> rounding.money(amount, currency, REPORT));
        return new ExpenseSummaryReport(rounding.money(total, currency, REPORT), currency, byCategory, count);
    }
}
