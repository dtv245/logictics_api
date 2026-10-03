package com.company.logicstic.service.payroll.policy;
import com.company.logicstic.dto.payroll.PayrollConfigurationRequests.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.service.payroll.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class PayrollConfigurationService {
 private final PayrollJurisdictionRepository jurisdictions;
 private final TenantPayrollSettingsRepository settings;
 private final EmployeePayrollProfileRepository profiles;
 private final EmployeeRepository employees;
 private final PayrollPolicyVersionRepository policies;
 private final ObjectMapper json;

 @Transactional
 public PayrollJurisdiction setTenantDefault(PayrollJurisdiction value) {
  var tenant=settings.lockSettings().orElseThrow();
  tenant.setDefaultJurisdiction(jurisdiction(value)); return value;
 }
 @Transactional
 public ProfileView appendProfile(UUID employeeId,Profile request) {
  var employee=employees.findByIdForUpdate(employeeId).orElseThrow(() -> new BadRequestException("Payroll employee not found"));
  dates(request.effectiveFrom(),request.effectiveTo());
  if(request.workerClassification()==null) throw new BadRequestException("Worker classification required");
  var prior=profiles.findTopByEmployeeIdOrderByProfileVersionDesc(employeeId);
  if(prior.isPresent() && !request.effectiveFrom().isAfter(prior.get().getEffectiveFrom()))
    throw new BadRequestException("PAYROLL_PROFILE_VERSION_DATE","Profile version must have a later effective date");
  var p=new EmployeePayrollProfile(); p.setId(UUID.randomUUID()); p.setEmployee(employee);
  p.setJurisdiction(request.jurisdiction()==null?null:jurisdiction(request.jurisdiction()));
  p.setWorkerClassification(request.workerClassification()); p.setEffectiveFrom(request.effectiveFrom()); p.setEffectiveTo(request.effectiveTo());
  p.setActive(request.active()); p.setProfileVersion(prior.map(x -> x.getProfileVersion()+1).orElse(1));
  profiles.save(p);
  return new ProfileView(p.getId(),employeeId,request.jurisdiction(),p.getWorkerClassification(),p.getEffectiveFrom(),p.getEffectiveTo(),p.getProfileVersion(),p.getActive());
 }
 @Transactional
 public PayrollPolicy appendPolicy(Policy request) {
  dates(request.effectiveFrom(),request.effectiveTo());
  if(request.policyCode()==null || request.policyCode().isBlank() || request.policyCode().length()>80 || request.workerClassification()==null
   || request.calculatorKey()==null || request.calculatorKey().isBlank() || request.calculatorKey().length()>100
   || request.authoritativeSource()==null || request.authoritativeSource().isBlank() || request.authoritativeSource().length()>1000)
    throw new BadRequestException("PAYROLL_POLICY_INVALID","Explicit policy identity, classification, calculator key and authoritative source required");
  try { if(!json.readTree(request.configurationJson()).isObject()) throw new IllegalArgumentException(); }
  catch(Exception e) { throw new BadRequestException("PAYROLL_POLICY_CONFIG_INVALID","Configuration must be an explicit JSON object"); }
  var jurisdiction=jurisdiction(request.jurisdiction());
  jurisdictions.findByIdForUpdate(jurisdiction.getId()).orElseThrow(); // serializes versions within this exact scope
  var prior=policies.findTopByPolicyCodeOrderByPolicyVersionDesc(request.policyCode());
  if(prior.isPresent()) {
   var old=prior.get();
   if(!old.getJurisdiction().getId().equals(jurisdiction.getId()) || old.getWorkerClassification()!=request.workerClassification()
     || !request.effectiveFrom().isAfter(old.getEffectiveFrom()))
    throw new BadRequestException("PAYROLL_POLICY_VERSION_SCOPE","New version preserves jurisdiction/classification and starts later");
  }
  var p=new PayrollPolicyVersion(); p.setId(UUID.randomUUID()); p.setPolicyCode(request.policyCode()); p.setJurisdiction(jurisdiction);
  p.setWorkerClassification(request.workerClassification()); p.setEffectiveFrom(request.effectiveFrom()); p.setEffectiveTo(request.effectiveTo());
  p.setPolicyVersion(prior.map(x -> x.getPolicyVersion()+1).orElse(1)); p.setActive(request.active());
  p.setCurrency(CurrencyGuard.canonical(request.currency())); p.setCalculatorKey(request.calculatorKey());
  p.setAuthoritativeSource(request.authoritativeSource()); p.setConfigurationJson(request.configurationJson()); policies.save(p);
  return p.toDomain();
 }
 public PayrollJurisdictionEntity jurisdiction(PayrollJurisdiction value) {
  if(value==null) throw new BadRequestException("PAYROLL_JURISDICTION_NOT_CONFIGURED","Jurisdiction required");
  settings.lockSettings().orElseThrow(); // serialize creation of the same optional-code jurisdiction
  return jurisdictions.findByCountryCodeAndSubdivisionCodeAndLocalityCode(value.countryCode(),value.subdivisionCode(),value.localityCode())
   .orElseGet(() -> {
    var row=new PayrollJurisdictionEntity(); row.setId(UUID.randomUUID()); row.setCountryCode(value.countryCode());
    row.setSubdivisionCode(value.subdivisionCode()); row.setLocalityCode(value.localityCode()); return jurisdictions.saveAndFlush(row);
   });
 }
 private void dates(LocalDate from,LocalDate to) {
  if(from==null || to!=null && to.isBefore(from)) throw new BadRequestException("PAYROLL_POLICY_DATE_INVALID","Ordered effective dates required");
 }
}
