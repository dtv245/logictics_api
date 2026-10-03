package com.company.logicstic.service.payroll.policy;
import com.company.logicstic.service.payroll.domain.*;
import com.company.logicstic.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.UUID;
@Component @RequiredArgsConstructor
public class PayrollJurisdictionResolver {
 private final EmployeePayrollProfileRepository profiles;
 private final TenantPayrollSettingsRepository settings;
 public record Resolution(PayrollJurisdiction jurisdiction,WorkerClassification workerClassification,UUID profileId,Integer profileVersion,String source,String reason) {}
 public Resolution resolve(UUID employee,PayrollJurisdiction override,LocalDate effectiveDate) {
  var rows=profiles.findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescProfileVersionDesc(employee,effectiveDate);
  var newest=rows.isEmpty()?null:rows.getFirst();
  var profile=newest!=null && newest.getActive() && (newest.getEffectiveTo()==null || !effectiveDate.isAfter(newest.getEffectiveTo()))?newest:null;
  PayrollJurisdiction jurisdiction=override; String source=override==null?null:"WORK_PAYROLL_OVERRIDE";
  if(jurisdiction==null && profile!=null && profile.getJurisdiction()!=null) { jurisdiction=profile.getJurisdiction().toDomain(); source="EMPLOYEE_PROFILE"; }
  if(jurisdiction==null) {
   var tenant=settings.findById(1).orElse(null);
   if(tenant!=null && tenant.getDefaultJurisdiction()!=null) { jurisdiction=tenant.getDefaultJurisdiction().toDomain(); source="TENANT_DEFAULT"; }
  }
  var classification=profile==null?null:profile.getWorkerClassification();
  String reason=jurisdiction==null?"PAYROLL_JURISDICTION_NOT_CONFIGURED":classification==null?"PAYROLL_WORKER_CLASSIFICATION_NOT_CONFIGURED":null;
  return new Resolution(jurisdiction,classification,profile==null?null:profile.getId(),profile==null?null:profile.getProfileVersion(),source,reason);
 }
}
