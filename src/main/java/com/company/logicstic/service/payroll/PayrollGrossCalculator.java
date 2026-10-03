package com.company.logicstic.service.payroll;
import com.company.logicstic.entity.*;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.payroll.tax.PayrollTaxContext.SourceLine;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
@Component
public class PayrollGrossCalculator {
 public record Totals(BigDecimal gross,BigDecimal deductions,BigDecimal reimbursements,BigDecimal preTaxNet,List<SourceLine> lines) {}
 public Totals calculate(List<DriverSettlement> settlements,Function<UUID,List<SettlementLine>> linesForSettlement,
                        Function<UUID,SettlementLine> originalLine,String currency) {
  BigDecimal gross=BigDecimal.ZERO,deductions=BigDecimal.ZERO,reimbursements=BigDecimal.ZERO;
  List<SourceLine> explanation=new ArrayList<>(); Set<UUID> seen=new HashSet<>();
  for(var settlement:settlements) {
   CurrencyGuard.requireSameCurrency(currency,settlement.getCurrency());
   BigDecimal sourceNet=BigDecimal.ZERO;
   for(var line:linesForSettlement.apply(settlement.getId())) {
    if(!seen.add(line.getId())) throw new BadRequestException("PAYROLL_SOURCE_DUPLICATE","Settlement line selected twice");
    CurrencyGuard.requireSameCurrency(currency,line.getCurrency());
    if(line.getAmount()==null || line.getAmount().signum()<0) throw new BadRequestException("PAYROLL_SOURCE_INVALID","Settlement line amount invalid");
    var root=line; var sign=BigDecimal.ONE; Set<UUID> chain=new HashSet<>();
    while("REVERSAL".equals(root.getSettlement().getSettlementType())) {
     if(!chain.add(root.getId()) || chain.size()>100 || root.getSourceId()==null)
       throw new BadRequestException("PAYROLL_REVERSAL_LINEAGE_INVALID","Reversal source lineage cannot be proved");
     var reversed=originalLine.apply(root.getSourceId());
     if(reversed==null || root.getSettlement().getParentSettlement()==null
       || !root.getSettlement().getParentSettlement().getId().equals(reversed.getSettlement().getId())
       || root.getAmount().compareTo(reversed.getAmount())!=0
       || !root.getSettlement().getDriver().getId().equals(reversed.getSettlement().getDriver().getId()))
       throw new BadRequestException("PAYROLL_REVERSAL_LINEAGE_INVALID","Reversal source must match its parent's driver/line amount");
     CurrencyGuard.requireSameCurrency(currency,reversed.getCurrency()); root=reversed; sign=sign.negate();
    }
    var amount=line.getAmount().multiply(sign);
    switch(root.getLineClass()) {
     case "EARNING" -> {gross=gross.add(amount);sourceNet=sourceNet.add(amount);}
     case "DEDUCTION" -> {deductions=deductions.add(amount);sourceNet=sourceNet.subtract(amount);}
     case "REIMBURSEMENT" -> {reimbursements=reimbursements.add(amount);sourceNet=sourceNet.add(amount);}
     default -> throw new BadRequestException("PAYROLL_SOURCE_CLASS_INVALID","Unknown economic settlement line class");
    }
    explanation.add(new SourceLine(settlement.getId(),line.getId(),root.getId(),root.getLineClass(),amount,
             Boolean.TRUE.equals(root.getTaxable()),root.getSourceType(),root.getSourceId()));
   }
   if(settlement.getSettlementNet()==null || sourceNet.compareTo(settlement.getSettlementNet())!=0)
     throw new BadRequestException("PAYROLL_SETTLEMENT_RECONCILIATION_FAILED","Economic source lines do not reconcile to settlement net");
  }
  return new Totals(gross,deductions,reimbursements,gross.subtract(deductions).add(reimbursements),List.copyOf(explanation));
 }
}
