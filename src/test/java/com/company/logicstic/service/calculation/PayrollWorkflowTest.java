package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.*;
import com.company.logicstic.entity.PayrollRun;
import com.company.logicstic.repository.*;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PayrollWorkflowTest {
 @Test void reviewApprovalLockAreOrderedAndDoNotExposePaid() {
  var flow=new PayrollWorkflow();
  assertDoesNotThrow(() -> flow.requireTransition("CALCULATED","IN_REVIEW"));
  assertDoesNotThrow(() -> flow.requireTransition("IN_REVIEW","APPROVED"));
  assertDoesNotThrow(() -> flow.requireTransition("APPROVED","LOCKED"));
  assertThrows(BadRequestException.class,() -> flow.requireTransition("CALCULATED","LOCKED"));
  assertThrows(BadRequestException.class,() -> flow.requireTransition("LOCKED","PAID"));
 }
 @Test void unavailableRunCannotEnterReviewOrApproval() {
  var runs=mock(PayrollRunRepository.class);var items=mock(PayrollRunItemRepository.class);var employees=mock(EmployeeRepository.class);
  var run=new PayrollRun();run.setId(UUID.randomUUID());run.setStatus("VALIDATION_REQUIRED");var actor=UUID.randomUUID();
  when(runs.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));when(employees.existsById(actor)).thenReturn(true);
  var service=new PayrollWorkflowService(runs,items,employees,new PayrollWorkflow(),
    new PayrollReconciliationService(TestRoundingPolicies.standard()),mock(PayrollCalculationService.class),mock(PayslipService.class));
  assertThrows(BadRequestException.class,() -> service.transition(run.getId(),"IN_REVIEW",actor));
  assertThrows(BadRequestException.class,() -> service.transition(run.getId(),"APPROVED",actor));
  verify(runs,never()).saveAndFlush(any());assertEquals("VALIDATION_REQUIRED",run.getStatus());
 }
 @Test void unauthenticatedActorCannotChangeFinancialWorkflow() {
  var service=new PayrollWorkflowService(mock(PayrollRunRepository.class),mock(PayrollRunItemRepository.class),mock(EmployeeRepository.class),
      new PayrollWorkflow(),new PayrollReconciliationService(TestRoundingPolicies.standard()),mock(PayrollCalculationService.class),mock(PayslipService.class));
  assertEquals("PAYROLL_ACTOR_REQUIRED",assertThrows(BadRequestException.class,() -> service.transition(UUID.randomUUID(),"LOCKED",null)).getCode());
 }
}
