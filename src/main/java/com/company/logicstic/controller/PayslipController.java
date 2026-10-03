package com.company.logicstic.controller;
import com.company.logicstic.service.payroll.PayslipService;
import com.company.logicstic.dto.payroll.PayslipView;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.exception.ForbiddenException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import java.util.*;
@RestController @RequiredArgsConstructor
public class PayslipController {
 private final PayslipService payslips;
 private final EmployeeRepository employees;
 @PostMapping("/api/payroll/runs/{id}/payslips") public List<PayslipView> issue(@PathVariable UUID id,Authentication auth) {return payslips.issue(id,actor(auth));}
 @GetMapping("/api/driver/me/payslips") public List<PayslipView> mine(Authentication auth) {return payslips.mine(actor(auth));}
 @GetMapping("/api/payslips/{id}") public PayslipView get(@PathVariable UUID id,Authentication auth) {return payslips.get(id,actor(auth),payrollRole(auth));}
 @GetMapping(value="/api/payslips/{id}/pdf",produces="application/pdf")
 public ResponseEntity<byte[]> pdf(@PathVariable UUID id,Authentication auth) {
  var body=payslips.pdf(id,actor(auth),payrollRole(auth));
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"payslip-"+id+".pdf\"")
   .header(HttpHeaders.CACHE_CONTROL,"private, no-store").contentType(MediaType.APPLICATION_PDF).body(body);
 }
 private UUID actor(Authentication auth) {
  if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new ForbiddenException("Authenticated payslip employee required");
  return employees.findByEmail(auth.getName()).orElseThrow(() -> new ForbiddenException("Payslip actor must map to an employee")).getId();
 }
 private boolean payrollRole(Authentication auth) {
  return auth.getAuthorities().stream().anyMatch(a -> Set.of("ROLE_ADMIN","ROLE_ACCOUNTANT","ROLE_PAYROLL","ROLE_PAYROLL_MANAGER").contains(a.getAuthority()));
 }
}
