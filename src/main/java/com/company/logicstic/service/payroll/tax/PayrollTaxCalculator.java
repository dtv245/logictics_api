package com.company.logicstic.service.payroll.tax;
import com.company.logicstic.service.payroll.domain.PayrollPolicy;
public interface PayrollTaxCalculator {
    PayrollTaxResult calculate(PayrollTaxContext context, PayrollPolicy policy);
}
