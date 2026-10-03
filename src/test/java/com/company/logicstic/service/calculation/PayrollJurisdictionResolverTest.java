package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.policy.PayrollJurisdictionResolver;
import com.company.logicstic.service.payroll.domain.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PayrollJurisdictionResolverTest {
 private PayrollJurisdictionEntity jurisdiction(String country,String code) {
  var j=new PayrollJurisdictionEntity();j.setId(UUID.randomUUID());j.setCountryCode(country);j.setSubdivisionCode(code);return j;
 }
 @Test void hierarchySeparatesJurisdictionFromExplicitWorkerClassification() {
  var profiles=mock(EmployeePayrollProfileRepository.class);var settings=mock(TenantPayrollSettingsRepository.class);
  var driver=UUID.randomUUID();var date=LocalDate.of(2026,1,1);var tenant=new TenantPayrollSettings();tenant.setDefaultJurisdiction(jurisdiction("US","TENANT"));
  var profile=new EmployeePayrollProfile();profile.setId(UUID.randomUUID());profile.setProfileVersion(1);profile.setActive(true);
  profile.setWorkerClassification(WorkerClassification.CONTRACTOR);profile.setJurisdiction(jurisdiction("VN","PROFILE"));
  when(profiles.findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescProfileVersionDesc(driver,date)).thenReturn(List.of(profile));
  when(settings.findById(1)).thenReturn(Optional.of(tenant));var resolver=new PayrollJurisdictionResolver(profiles,settings);
  assertEquals("EMPLOYEE_PROFILE",resolver.resolve(driver,null,date).source());
  assertEquals("WORK_PAYROLL_OVERRIDE",resolver.resolve(driver,new PayrollJurisdiction("US",null,"WORK"),date).source());
  assertEquals(WorkerClassification.CONTRACTOR,resolver.resolve(driver,null,date).workerClassification());
  profile.setJurisdiction(null);assertEquals("TENANT_DEFAULT",resolver.resolve(driver,null,date).source());
 }
 @Test void expiredNewestProfileDoesNotResurrectHistoricalWorkerClassification() {
  var profiles=mock(EmployeePayrollProfileRepository.class);var settings=mock(TenantPayrollSettingsRepository.class);
  var driver=UUID.randomUUID();var date=LocalDate.of(2026,3,1);var newest=new EmployeePayrollProfile();newest.setActive(true);newest.setEffectiveTo(date.minusDays(1));
  var older=new EmployeePayrollProfile();older.setActive(true);older.setWorkerClassification(WorkerClassification.EMPLOYEE);
  var tenant=new TenantPayrollSettings();tenant.setDefaultJurisdiction(jurisdiction("VN",null));
  when(profiles.findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescProfileVersionDesc(driver,date)).thenReturn(List.of(newest,older));
  when(settings.findById(1)).thenReturn(Optional.of(tenant));
  var result=new PayrollJurisdictionResolver(profiles,settings).resolve(driver,null,date);
  assertNull(result.workerClassification());assertEquals("PAYROLL_WORKER_CLASSIFICATION_NOT_CONFIGURED",result.reason());
 }
 @Test void missingJurisdictionIsExplicitUnavailableReason() {
  var profiles=mock(EmployeePayrollProfileRepository.class);var settings=mock(TenantPayrollSettingsRepository.class);
  var driver=UUID.randomUUID();var date=LocalDate.of(2026,1,1);
  when(profiles.findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescProfileVersionDesc(driver,date)).thenReturn(List.of());
  when(settings.findById(1)).thenReturn(Optional.empty());
  assertEquals("PAYROLL_JURISDICTION_NOT_CONFIGURED",new PayrollJurisdictionResolver(profiles,settings).resolve(driver,null,date).reason());
 }
}
