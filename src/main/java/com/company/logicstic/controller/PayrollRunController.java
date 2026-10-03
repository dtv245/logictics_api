package com.company.logicstic.controller;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.service.payroll.PayrollCalculationService;
import com.company.logicstic.service.payroll.PayrollWorkflowService;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController @RequestMapping("/api/payroll/runs") @RequiredArgsConstructor
public class PayrollRunController {
 private final PayrollCalculationService calculation;
 private final PayrollWorkflowService workflow;
 private final EmployeeRepository employees;
 @PostMapping("/calculate") public PayrollRunView calculate(@Valid @RequestBody CalculatePayrollRequest request) {return calculation.calculate(request);}
 @PostMapping("/{id}/recalculate") public PayrollRunView recalculate(@PathVariable UUID id) {return calculation.recalculate(id);}
 @GetMapping("/{id}") public PayrollRunView get(@PathVariable UUID id) {return calculation.get(id);}
 @PostMapping("/{id}/submit-review") public PayrollRunView review(@PathVariable UUID id,Authentication auth) {return workflow.transition(id,"IN_REVIEW",actor(auth));}
 @PostMapping("/{id}/approve") public PayrollRunView approve(@PathVariable UUID id,Authentication auth) {return workflow.transition(id,"APPROVED",actor(auth));}
 @PostMapping("/{id}/lock") public PayrollRunView lock(@PathVariable UUID id,Authentication auth) {return workflow.transition(id,"LOCKED",actor(auth));}
 private UUID actor(Authentication auth) {
  if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
   throw new ForbiddenException("Authenticated payroll actor required");
  return employees.findByEmail(auth.getName()).orElseThrow(() -> new ForbiddenException("Payroll actor must map to an employee")).getId();
 }
}
