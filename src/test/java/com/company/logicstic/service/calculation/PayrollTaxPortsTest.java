package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.domain.*;
import com.company.logicstic.service.payroll.tax.*;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PayrollTaxPortsTest {
 private PayrollPolicy policy(String key) {return new PayrollPolicy(UUID.randomUUID(),"fixture",new PayrollJurisdiction("VN",null,"LOCAL"),
 WorkerClassification.CONTRACTOR,LocalDate.of(2026,1,1),null,1,true,"USD",key,"fixture://source","{}");}
 private PayrollTaxContext context() {return new PayrollTaxContext(UUID.randomUUID(),new PayrollJurisdiction("VN",null,"LOCAL"),WorkerClassification.CONTRACTOR,
 LocalDate.of(2026,1,1),"USD",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,List.of(),Map.of());}
 @Test void genericJurisdictionDoesNotRequireStateAndValidatesIsoCountry() {
  assertEquals(new PayrollJurisdiction("vn",null," local "),new PayrollJurisdiction("VN",null,"LOCAL"));
  assertThrows(BadRequestException.class,() -> new PayrollJurisdiction("ZZ",null,null));
 }
 @Test void contractorWithoutAdapterDoesNotReceiveZeroTax() {
  var result=new ConfiguredPayrollTaxCalculator(List.of()).calculate(context(),policy("missing"));
  assertEquals("UNAVAILABLE",result.availability());assertNull(result.incomeTaxAmount());assertEquals("PAYROLL_TAX_CALCULATOR_NOT_CONFIGURED",result.reason());
 }
 @Test void invalidAdapterAmountsAndVersionFailClosed() {
  var adapter=new PayrollTaxAdapter() {
   public String key() {return "bad";}
   public PayrollTaxResult calculate(PayrollTaxContext context,PayrollPolicy policy) {
    return new PayrollTaxResult("AVAILABLE",null,"USD",new BigDecimal("-1"),BigDecimal.ZERO,"bad",null,Map.of(),Map.of());
   }
  };
  assertEquals("PAYROLL_TAX_RESULT_INVALID",new ConfiguredPayrollTaxCalculator(List.of(adapter)).calculate(context(),policy("bad")).reason());
 }
 @Test void duplicateAdapterKeysFailStartup() {
  var adapter=new PayrollTaxAdapter() {public String key(){return "x";}public PayrollTaxResult calculate(PayrollTaxContext c,PayrollPolicy p){return null;}};
  assertThrows(IllegalStateException.class,() -> new ConfiguredPayrollTaxCalculator(List.of(adapter,adapter)));
 }
}
