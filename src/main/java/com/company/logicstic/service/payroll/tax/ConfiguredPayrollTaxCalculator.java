package com.company.logicstic.service.payroll.tax;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.service.payroll.domain.PayrollPolicy;
import org.springframework.stereotype.Component;
import java.util.*;
@Component @org.springframework.context.annotation.Primary
public class ConfiguredPayrollTaxCalculator implements PayrollTaxCalculator {
 private final Map<String,PayrollTaxAdapter> adapters;
 public ConfiguredPayrollTaxCalculator(List<PayrollTaxAdapter> available) {
  Map<String,PayrollTaxAdapter> map=new HashMap<>();
  for(var a:available) if(a.key()==null || map.putIfAbsent(a.key(),a)!=null) throw new IllegalStateException("Duplicate/missing payroll calculator key");
  adapters=Map.copyOf(map);
 }
 public PayrollTaxResult calculate(PayrollTaxContext context,PayrollPolicy policy) {
  var adapter=adapters.get(policy.calculatorKey());
  if(adapter==null) return PayrollTaxResult.unavailable("PAYROLL_TAX_CALCULATOR_NOT_CONFIGURED",context.currency(),policy.calculatorKey());
  var result=adapter.calculate(context,policy);
  if(result==null || !"AVAILABLE".equals(result.availability())) return result==null?
      PayrollTaxResult.unavailable("PAYROLL_TAX_RESULT_UNAVAILABLE",context.currency(),policy.calculatorKey()):result;
  CurrencyGuard.requireSameCurrency(context.currency(),result.currency());
  if(result.incomeTaxAmount()==null || result.insuranceAmount()==null || result.incomeTaxAmount().signum()<0
     || result.insuranceAmount().signum()<0 || !policy.calculatorKey().equals(result.calculatorKey())
     || result.calculatorVersion()==null || result.calculatorVersion().isBlank() || result.inputs()==null || result.outputs()==null)
      return PayrollTaxResult.unavailable("PAYROLL_TAX_RESULT_INVALID",context.currency(),policy.calculatorKey());
  return result;
 }
}
