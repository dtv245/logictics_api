package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.*;
import com.company.logicstic.service.payroll.SettlementReconciliationService;
import com.company.logicstic.exception.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettlementReconciliationServiceTest {
    final SettlementReconciliationService service = new SettlementReconciliationService();
    DriverSettlement settlement() {
        var s = new DriverSettlement(); s.setCurrency("USD"); s.setGrossEarnings(new BigDecimal("100")); s.setBonusAmount(new BigDecimal("100"));
        s.setDeductionAmount(new BigDecimal("10")); s.setReimbursementAmount(new BigDecimal("5")); s.setSettlementNet(new BigDecimal("95")); return s;
    }
    SettlementLine line(String kind, String amount) {
        var l = new SettlementLine(); l.setLineClass(kind); l.setAmount(new BigDecimal(amount)); l.setCurrency("USD"); return l;
    }
    @Test void independentlyReconcilesGrossDeductionsReimbursementsAndNet() {
        assertDoesNotThrow(() -> service.reconcile(settlement(),List.of(line("EARNING","100"),line("DEDUCTION","10"),line("REIMBURSEMENT","5"))));
    }
    @Test void mismatchedHeaderNetComponentsAndNegativeLinesFail() {
        var lines = List.of(line("EARNING","100"),line("DEDUCTION","10"),line("REIMBURSEMENT","5"));
        var s = settlement(); s.setSettlementNet(new BigDecimal("100")); assertThrows(BadRequestException.class, () -> service.reconcile(s,lines));
        var wrongComponent = settlement(); wrongComponent.setBonusAmount(new BigDecimal("90")); assertThrows(BadRequestException.class, () -> service.reconcile(wrongComponent,lines));
        assertThrows(BadRequestException.class, () -> service.reconcile(settlement(),List.of(line("EARNING","-100"))));
    }
    @Test void mixedCurrencyCannotReconcileEvenWhenNumbersMatch() {
        var l = line("EARNING","100"); l.setCurrency("VND");
        assertThrows(CurrencyMismatchException.class, () -> service.reconcile(settlement(),List.of(l)));
    }
}
