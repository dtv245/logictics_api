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
import java.util.List;

@Component @RequiredArgsConstructor
public class PercentagePayCalculator {
    private final FinancialRoundingPolicy rounding;
    private final InvoiceReconciliationService reconciliation;
    public record Result(UUID policyId, Integer policyVersion, UUID invoiceId, String revenueBasis,
            BigDecimal eligibleRevenue, BigDecimal ratio, BigDecimal rawAmount, BigDecimal amount,
            String currency, String roundingPolicyVersion,List<DocumentRevenue> documents) {}
    public record DocumentRevenue(UUID invoiceId,String purpose,int economicSign,String status,BigDecimal eligibleSubtotal) { }
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
                raw,rounding.money(raw,currency,FinancialRoundingPolicy.Boundary.ALLOCATION),currency,rounding.version(),
                List.of(new DocumentRevenue(invoice.getId(),invoice.getInvoicePurpose(),invoice.economicSign(),invoice.getStatus(),invoice.getSubtotalAmount())));
    }
    public Result calculateBillingChain(List<Invoice> invoices,DriverPayPolicy policy) {
        String basis=policy.getRevenueBasis();
        if(!"PERCENT_REVENUE".equals(policy.getPayMethod()) || basis==null
                || !java.util.Set.of("INVOICE_SUBTOTAL","PRIMARY_INVOICE_REVENUE","NET_ELIGIBLE_REVENUE").contains(basis))
            throw new BadRequestException("REVENUE_BASIS_UNAVAILABLE","Explicit versioned supported revenue basis required");
        String currency=CurrencyGuard.canonical(policy.getCurrency());
        var matching=invoices.stream().filter(i->currency.equals(CurrencyGuard.canonical(i.getSubtotalCurrency()))).toList();
        if(matching.isEmpty()) {
            if(!invoices.isEmpty())CurrencyGuard.requireSameCurrency(currency,invoices.getFirst().getSubtotalCurrency());
            throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED","Eligible billing documents required");
        }
        var primaries=matching.stream().filter(i->"PRIMARY".equals(i.getInvoicePurpose())).toList();
        // INVOICE_SUBTOTAL is explicit legacy compatibility, never a default or net-chain alias.
        if(primaries.isEmpty() && "INVOICE_SUBTOTAL".equals(basis)) {
            var legacy=matching.stream().filter(i->i.getInvoicePurpose()==null).toList();
            if(legacy.size()!=1)throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED","Legacy basis requires exactly one unclassified invoice");
            return calculate(legacy.getFirst(),policy);
        }
        if(primaries.size()!=1)throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED","Explicit PRIMARY identity required; no inferred legacy purpose");
        var primary=primaries.getFirst();List<DocumentRevenue> sources=new java.util.ArrayList<>();BigDecimal revenue=BigDecimal.ZERO;
        var selected="NET_ELIGIBLE_REVENUE".equals(basis)?matching:List.of(primary);
        for(var invoice:selected.stream().sorted(java.util.Comparator.comparing(i->i.getId().toString())).toList()) {
            if(!primary.getBillingChainId().equals(invoice.getBillingChainId()) || invoice.getInvoicePurpose()==null)
                throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED","Explicit consistent billing chain required");
            if(!InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue())continue;
            reconciliation.reconcile(invoice);CurrencyGuard.requireSameCurrency(currency,invoice.getSubtotalCurrency());
            int sign=invoice.economicSign();BigDecimal subtotal=invoice.getSubtotalAmount();
            revenue=revenue.add(subtotal.multiply(BigDecimal.valueOf(sign)));
            sources.add(new DocumentRevenue(invoice.getId(),invoice.getInvoicePurpose(),sign,invoice.getStatus(),subtotal));
        }
        BigDecimal ratio=policy.getRevenuePercentage();
        if(sources.isEmpty() || revenue.signum()<0)throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED","Eligible nonnegative economic revenue required");
        if(ratio==null || ratio.signum()<0 || ratio.compareTo(BigDecimal.ONE)>0)
            throw new BadRequestException("REVENUE_RATIO_INVALID","Revenue percentage is a ratio in [0,1]");
        BigDecimal raw=revenue.multiply(ratio);
        return new Result(policy.getId(),policy.getPolicyVersion(),primary.getId(),basis,revenue,ratio,raw,
                rounding.money(raw,currency,FinancialRoundingPolicy.Boundary.ALLOCATION),currency,rounding.version(),List.copyOf(sources));
    }
}
