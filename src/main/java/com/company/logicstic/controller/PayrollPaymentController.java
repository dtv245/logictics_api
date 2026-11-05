package com.company.logicstic.controller;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.service.payroll.payment.*;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.payroll.PayrollNoPaymentDispositionService;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.common.PaginationHeaders;
import com.company.logicstic.common.PaginationValidator;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/api/payroll") @RequiredArgsConstructor
public class PayrollPaymentController {
 private final PayrollPaymentSchedulingService scheduling;
 private final PayrollPaymentDispatchService dispatch;
 private final PayrollManualBankReconciliationService bankReconciliation;
 private final PayrollNoPaymentDispositionService noPaymentDisposition;
 private final EmployeeRepository employees;
 @PostMapping("/items/{id}/payments") public PayrollPaymentView schedule(@PathVariable UUID id,@Valid @RequestBody SchedulePayrollPaymentRequest r,Authentication auth) {return scheduling.schedule(id,r,actor(auth));}
 @GetMapping("/items/{id}/payments") public ResponseEntity<List<PayrollPaymentView>> list(
   @PathVariable UUID id,
   @RequestParam(defaultValue="1") Integer page,
   @RequestParam(defaultValue="20") Integer pageSize) {
  var pageable=PaginationValidator.pageRequest(page,pageSize,Sort.by("attemptNumber").ascending());
  var result=scheduling.list(id,pageable);
  return ResponseEntity.ok().headers(PaginationHeaders.from(result)).body(result.getContent());
 }
 @PostMapping("/items/{id}/no-payment-required") public PayrollRunView markNoPaymentRequired(
   @PathVariable UUID id,@Valid @RequestBody NoPaymentRequiredRequest request,Authentication auth) {
  return noPaymentDisposition.markNoPaymentRequired(id,request,actor(auth));
 }
 @PostMapping("/payments/{id}/dispatch") public PayrollPaymentView dispatch(@PathVariable UUID id) {return dispatch.dispatch(id);}
 @GetMapping("/reconciliation-cases") public org.springframework.data.domain.Page<PayrollReconciliationCaseView> openReconciliationCases(
   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
  return bankReconciliation.openCases(page,size);
 }
 @PostMapping("/payments/{id}/reconcile-bank") public PayrollPaymentEventView reconcileBank(
   @PathVariable UUID id,@Valid @RequestBody ManualPayrollBankReconciliationRequest request,Authentication auth) {
  return bankReconciliation.reconcile(id,request,actor(auth));
 }
 private UUID actor(Authentication auth) {
  if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new ForbiddenException("Authenticated payroll actor required");
  return employees.findByEmail(auth.getName()).orElseThrow(() -> new ForbiddenException("Payroll actor must map to an employee")).getId();
 }
}
