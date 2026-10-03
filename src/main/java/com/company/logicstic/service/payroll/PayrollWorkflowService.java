package com.company.logicstic.service.payroll;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.dto.payroll.PayrollRunView;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.time.*;
import java.time.temporal.ChronoUnit;
@Service @RequiredArgsConstructor
public class PayrollWorkflowService {
 private final PayrollRunRepository runs;
 private final PayrollRunItemRepository items;
 private final EmployeeRepository employees;
 private final PayrollWorkflow workflow;
 private final PayrollReconciliationService reconciliation;
 private final PayrollCalculationService calculation;
 @Transactional
 public PayrollRunView transition(UUID id,String target,UUID actor) {
  if(actor==null || !employees.existsById(actor)) throw new BadRequestException("PAYROLL_ACTOR_REQUIRED","Persisted authenticated payroll actor required");
  var run=runs.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Payroll run not found"));
  if(!java.util.Set.of("IN_REVIEW","APPROVED","LOCKED").contains(target)) throw new BadRequestException("PAYROLL_TRANSITION_INVALID","Review/approval/lock target required");
  if(!target.equals(run.getStatus())) {
   workflow.requireTransition(run.getStatus(),target);
   if(run.getValidationReason()!=null || run.getCalculationSnapshotJson()==null)
    throw new BadRequestException("PAYROLL_VALIDATION_REQUIRED","Payroll validation must be resolved before finalization");
   reconciliation.requireFinalizable(run,items.findByPayrollRunIdOrderById(id));
   var now=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);run.setStatus(target);
   switch(target) {
    case "IN_REVIEW" -> {run.setReviewedAt(now);run.setReviewedBy(actor);}
    case "APPROVED" -> {run.setApprovedAt(now);run.setApprovedBy(actor);}
    case "LOCKED" -> {run.setLockedAt(now);run.setLockedBy(actor);}
   }
   runs.saveAndFlush(run);
  }
  return calculation.view(run);
 }
}
