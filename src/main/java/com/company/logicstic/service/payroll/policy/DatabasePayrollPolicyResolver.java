package com.company.logicstic.service.payroll.policy;
import com.company.logicstic.entity.PayrollPolicyVersion;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.payroll.domain.*;
import com.company.logicstic.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.LinkedHashMap;
@Component @RequiredArgsConstructor
public class DatabasePayrollPolicyResolver implements PayrollPolicyResolver {
 private final PayrollJurisdictionRepository jurisdictions;
 private final PayrollPolicyVersionRepository policies;
 @Transactional(readOnly=true)
 public PayrollPolicy resolve(PayrollJurisdiction jurisdiction,WorkerClassification classification,LocalDate date) {
  if(jurisdiction==null) throw new BadRequestException("PAYROLL_JURISDICTION_NOT_CONFIGURED","Payroll jurisdiction unresolved");
  if(classification==null) throw new BadRequestException("PAYROLL_WORKER_CLASSIFICATION_NOT_CONFIGURED","Worker classification unresolved");
  if(date==null) throw new BadRequestException("PAYROLL_EFFECTIVE_DATE_REQUIRED","Explicit payroll effective date required");
  var row=jurisdictions.findByCountryCodeAndSubdivisionCodeAndLocalityCode(jurisdiction.countryCode(),jurisdiction.subdivisionCode(),jurisdiction.localityCode())
     .orElseThrow(() -> new BadRequestException("PAYROLL_POLICY_NOT_CONFIGURED","No exact-jurisdiction policy configured"));
  var latest=new LinkedHashMap<String,PayrollPolicyVersion>();
  for(var policy:policies.findByJurisdictionIdAndWorkerClassificationAndEffectiveFromLessThanEqualOrderByEffectiveFromDescPolicyVersionDesc(row.getId(),classification,date))
      latest.putIfAbsent(policy.getPolicyCode(),policy);
  var available=latest.values().stream().filter(p -> p.getActive() && (p.getEffectiveTo()==null || !date.isAfter(p.getEffectiveTo()))).toList();
  if(available.isEmpty()) throw new BadRequestException("PAYROLL_POLICY_NOT_CONFIGURED","No active effective exact-jurisdiction policy configured");
  if(available.size()!=1) throw new BadRequestException("PAYROLL_POLICY_AMBIGUOUS","Multiple effective policy families require configuration resolution");
  return available.getFirst().toDomain();
 }
}
