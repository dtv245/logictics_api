package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.entity.DriverSettlement;
import com.company.logicstic.entity.SettlementLine;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class SettlementReconciliationService {
    public void reconcile(DriverSettlement settlement, List<SettlementLine> lines) {
        BigDecimal gross=BigDecimal.ZERO,deductions=BigDecimal.ZERO,reimbursements=BigDecimal.ZERO;
        for (var line : lines) {
            CurrencyGuard.requireSameCurrency(settlement.getCurrency(),line.getCurrency());
            if (line.getAmount()==null || line.getAmount().signum()<0) throw invalid();
            switch (line.getLineClass()) {
                case "EARNING" -> gross=gross.add(line.getAmount());
                case "DEDUCTION" -> deductions=deductions.add(line.getAmount());
                case "REIMBURSEMENT" -> reimbursements=reimbursements.add(line.getAmount());
                default -> throw invalid();
            }
        }
        same(gross,settlement.getGrossEarnings()); same(deductions,settlement.getDeductionAmount());
        same(reimbursements,settlement.getReimbursementAmount()); same(gross.subtract(deductions).add(reimbursements),settlement.getSettlementNet());
        same(gross,settlement.getMileagePay().add(settlement.getLoadPay()).add(settlement.getPercentagePay())
                .add(settlement.getHourlyPay()).add(settlement.getAccessorialPay()).add(settlement.getBonusAmount()));
    }
    private void same(BigDecimal expected, BigDecimal actual) { if (actual==null || expected.compareTo(actual)!=0) throw invalid(); }
    private BadRequestException invalid() { return new BadRequestException("SETTLEMENT_RECONCILIATION_FAILED", "Settlement header and eligible lines do not reconcile"); }
}
