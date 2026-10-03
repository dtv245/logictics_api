package com.company.logicstic.controller;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.service.payroll.payment.*;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.exception.ForbiddenException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/api/payroll") @RequiredArgsConstructor
public class PayrollPaymentController {
 private final PayrollPaymentSchedulingService scheduling;
 private final PayrollPaymentDispatchService dispatch;
 private final EmployeeRepository employees;
 @PostMapping("/items/{id}/payments") public PayrollPaymentView schedule(@PathVariable UUID id,@Valid @RequestBody SchedulePayrollPaymentRequest r,Authentication auth) {return scheduling.schedule(id,r,actor(auth));}
 @GetMapping("/items/{id}/payments") public List<PayrollPaymentView> list(@PathVariable UUID id) {return scheduling.list(id);}
 @PostMapping("/payments/{id}/dispatch") public PayrollPaymentView dispatch(@PathVariable UUID id) {return dispatch.dispatch(id);}
 private UUID actor(Authentication auth) {
  if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new ForbiddenException("Authenticated payroll actor required");
  return employees.findByEmail(auth.getName()).orElseThrow(() -> new ForbiddenException("Payroll actor must map to an employee")).getId();
 }
}
