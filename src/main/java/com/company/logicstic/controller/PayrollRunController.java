package com.company.logicstic.controller;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.service.payroll.PayrollCalculationService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController @RequestMapping("/api/payroll/runs") @RequiredArgsConstructor
public class PayrollRunController {
 private final PayrollCalculationService calculation;
 @PostMapping("/calculate") public PayrollRunView calculate(@Valid @RequestBody CalculatePayrollRequest request) {return calculation.calculate(request);}
 @PostMapping("/{id}/recalculate") public PayrollRunView recalculate(@PathVariable UUID id) {return calculation.recalculate(id);}
 @GetMapping("/{id}") public PayrollRunView get(@PathVariable UUID id) {return calculation.get(id);}
}
