package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.PayrollGrossCalculator;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PayrollGrossCalculatorTest {
 private DriverSettlement settlement(String type,String net,DriverSettlement parent) {
  var s=new DriverSettlement();s.setId(UUID.randomUUID());s.setSettlementType(type);s.setParentSettlement(parent);s.setCurrency("USD");s.setSettlementNet(new BigDecimal(net));
  var driver=new Employee();driver.setId(parent==null?UUID.randomUUID():parent.getDriver().getId());s.setDriver(driver);return s;
 }
 private SettlementLine line(DriverSettlement s,String cls,String amount,SettlementLine source) {
  var l=new SettlementLine();l.setId(UUID.randomUUID());l.setSettlement(s);l.setLineClass(cls);l.setAmount(new BigDecimal(amount));l.setCurrency("USD");l.setTaxable(true);
  if(source!=null) l.setSourceId(source.getId());return l;
 }
 @Test void signedEconomicComponentsCancelOriginalWithoutInflatingGross() {
  var original=settlement("ORIGINAL","95",null);var reversal=settlement("REVERSAL","-95",original);
  var earn=line(original,"EARNING","100",null);var ded=line(original,"DEDUCTION","10",null);var reimb=line(original,"REIMBURSEMENT","5",null);
  var originals=List.of(earn,ded,reimb);var reversed=List.of(line(reversal,"DEDUCTION","100",earn),line(reversal,"EARNING","10",ded),line(reversal,"DEDUCTION","5",reimb));
  var byId=new HashMap<UUID,SettlementLine>();originals.forEach(l -> byId.put(l.getId(),l));
  var result=new PayrollGrossCalculator().calculate(List.of(original,reversal),id -> id.equals(original.getId())?originals:reversed,byId::get,"USD");
  assertEquals(0,result.gross().signum());assertEquals(0,result.deductions().signum());assertEquals(0,result.reimbursements().signum());assertEquals(0,result.preTaxNet().signum());
  assertTrue(result.lines().stream().anyMatch(l -> l.signedAmount().compareTo(new BigDecimal("-100"))==0 && l.economicClass().equals("EARNING")));
 }
 @Test void unresolvedReversalDoesNotGuessFromHeaderClass() {
  var original=settlement("ORIGINAL","100",null);var reversal=settlement("REVERSAL","-100",original);
  var missing=line(reversal,"DEDUCTION","100",null);
  assertEquals("PAYROLL_REVERSAL_LINEAGE_INVALID",assertThrows(BadRequestException.class,() -> new PayrollGrossCalculator().calculate(List.of(reversal),id -> List.of(missing),id -> null,"USD")).getCode());
 }
 @Test void headerSourceReconciliationIsMandatory() {
  var s=settlement("ORIGINAL","999",null);var earning=line(s,"EARNING","100",null);
  assertEquals("PAYROLL_SETTLEMENT_RECONCILIATION_FAILED",assertThrows(BadRequestException.class,() -> new PayrollGrossCalculator().calculate(List.of(s),id -> List.of(earning),id -> null,"USD")).getCode());
 }
}
