package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.common.enums.InvoiceStatus;
import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.calculation.InvoiceReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class PercentagePayCalculator {
    private final FinancialRoundingPolicy rounding;
    private final InvoiceReconciliationService reconciliation;
    public record Result(UUID policyId, Integer policyVersion, UUID invoiceId, String revenueBasis,
            BigDecimal eligibleRevenue, BigDecimal ratio, BigDecimal rawAmount, BigDecimal amount,
            String currency, String roundingPolicyVersion) {}
    public Result calculate(Invoice invoice, DriverPayPolicy policy) {
        if (!"PERCENT_REVENUE".equals(policy.getPayMethod()) || !"INVOICE_SUBTOTAL".equals(policy.getRevenueBasis()))
            throw new BadRequestException("REVENUE_BASIS_UNAVAILABLE", "Explicit supported INVOICE_SUBTOTAL required");
        if (invoice == null || invoice.getId() == null || !InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue()
                || invoice.getSubtotalAmount() == null || invoice.getSubtotalAmount().signum() < 0)
            throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED", "Eligible reconciled invoice subtotal required");
        BigDecimal ratio = policy.getRevenuePercentage();
        if (ratio == null || ratio.signum() < 0 || ratio.compareTo(BigDecimal.ONE) > 0)
            throw new BadRequestException("REVENUE_RATIO_INVALID", "Revenue percentage is a ratio in [0,1]");
        String currency = CurrencyGuard.canonical(policy.getCurrency());
        CurrencyGuard.requireSameCurrency(currency,invoice.getSubtotalCurrency()); reconciliation.reconcile(invoice);
        BigDecimal raw = invoice.getSubtotalAmount().multiply(ratio);
        return new Result(policy.getId(),policy.getPolicyVersion(),invoice.getId(),policy.getRevenueBasis(),invoice.getSubtotalAmount(),ratio,
                raw,rounding.money(raw,currency,FinancialRoundingPolicy.Boundary.ALLOCATION),currency,rounding.version());
    }
}
