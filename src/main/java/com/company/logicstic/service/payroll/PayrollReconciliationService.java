package com.company.logicstic.service.payroll;
import com.company.logicstic.common.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
@Component @RequiredArgsConstructor
public class PayrollReconciliationService {
 private final FinancialRoundingPolicy rounding;
 public void requireFinalizable(PayrollRun run,List<PayrollRunItem> items) {
  if(items.isEmpty()) throw new BadRequestException("PAYROLL_ITEMS_REQUIRED","A payroll run must contain financial items");
  for(var item:items) {
   CurrencyGuard.requireSameCurrency(run.getCurrency(),item.getCurrency());
   if(!"AVAILABLE".equals(item.getTaxAvailability()) || item.getValidationReason()!=null || item.getPayrollPolicy()==null
      || item.getJurisdiction()==null || item.getWorkerClassification()==null || item.getEffectiveDate()==null || item.getPayrollPolicyVersion()==null
      || item.getIncomeTaxAmount()==null || item.getInsuranceAmount()==null || item.getNetAmount()==null
      || item.getGrossAmount().signum()<0 || item.getOtherDeductionAmount().signum()<0 || item.getReimbursementAmount().signum()<0
      || item.getIncomeTaxAmount().signum()<0 || item.getInsuranceAmount().signum()<0 || item.getNetAmount().signum()<0)
      throw new BadRequestException("PAYROLL_VALIDATION_REQUIRED","Resolved statutory policy/calculation and nonnegative payable amounts required");
   var net=rounding.money(item.getGrossAmount().subtract(item.getOtherDeductionAmount()).add(item.getReimbursementAmount())
         .subtract(item.getIncomeTaxAmount()).subtract(item.getInsuranceAmount()),item.getCurrency(),FinancialRoundingPolicy.Boundary.ALLOCATION);
   if(net.compareTo(item.getNetAmount())!=0)
       throw new BadRequestException("PAYROLL_RECONCILIATION_FAILED","Gross - deductions - statutory charges + reimbursement differs from net");
   if(!item.getPayrollPolicyVersion().equals(item.getPayrollPolicy().getPolicyVersion()))
       throw new BadRequestException("PAYROLL_POLICY_VERSION_MISMATCH","Immutable policy identity/version mismatch");
  }
 }
}
