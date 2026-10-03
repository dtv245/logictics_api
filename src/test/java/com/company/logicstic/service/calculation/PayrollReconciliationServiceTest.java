package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.PayrollReconciliationService;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PayrollReconciliationServiceTest {
 private PayrollRunItem item() {
  var i=new PayrollRunItem();i.setCurrency("USD");i.setTaxAvailability("AVAILABLE");i.setGrossAmount(new BigDecimal("100"));i.setIncomeTaxAmount(new BigDecimal("7"));
  i.setInsuranceAmount(new BigDecimal("3"));i.setOtherDeductionAmount(new BigDecimal("2"));i.setReimbursementAmount(new BigDecimal("5"));i.setNetAmount(new BigDecimal("93"));
  i.setJurisdiction(new PayrollJurisdictionEntity());i.setWorkerClassification(com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR);
  i.setEffectiveDate(LocalDate.of(2026,1,31));var p=new PayrollPolicyVersion();p.setPolicyVersion(1);i.setPayrollPolicy(p);i.setPayrollPolicyVersion(1);return i;
 }
 @Test void checksFormulaAndDetectsTamperedNet() {
  var run=new PayrollRun();run.setCurrency("USD");var i=item();var service=new PayrollReconciliationService(TestRoundingPolicies.standard());
  assertDoesNotThrow(() -> service.requireFinalizable(run,List.of(i)));i.setNetAmount(new BigDecimal("94"));
  assertEquals("PAYROLL_RECONCILIATION_FAILED",assertThrows(BadRequestException.class,() -> service.requireFinalizable(run,List.of(i))).getCode());
 }
 @Test void unavailableTaxAndPolicyVersionDriftPreventFinalization() {
  var run=new PayrollRun();run.setCurrency("USD");var i=item();var service=new PayrollReconciliationService(TestRoundingPolicies.standard());
  i.setTaxAvailability("UNAVAILABLE");i.setIncomeTaxAmount(null);
  assertEquals("PAYROLL_VALIDATION_REQUIRED",assertThrows(BadRequestException.class,() -> service.requireFinalizable(run,List.of(i))).getCode());
  var drift=item();drift.setPayrollPolicyVersion(2);
  assertEquals("PAYROLL_POLICY_VERSION_MISMATCH",assertThrows(BadRequestException.class,() -> service.requireFinalizable(run,List.of(drift))).getCode());
 }
}
