package com.company.logicstic.service.payroll.tax;
import java.math.BigDecimal;
import java.util.Map;
public record PayrollTaxResult(String availability,String reason,String currency,BigDecimal incomeTaxAmount,
 BigDecimal insuranceAmount,String calculatorKey,String calculatorVersion,Map<String,Object> inputs,Map<String,Object> outputs) {
 public static PayrollTaxResult unavailable(String reason,String currency,String key) {
 return new PayrollTaxResult("UNAVAILABLE",reason,currency,null,null,key,null,Map.of(),Map.of());
 }
}
