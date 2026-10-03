package com.company.logicstic.controller;
import com.company.logicstic.service.payroll.policy.PayrollConfigurationService;
import com.company.logicstic.service.payroll.domain.*;
import com.company.logicstic.dto.payroll.PayrollConfigurationRequests.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController @RequestMapping("/api/payroll/configuration") @RequiredArgsConstructor
public class PayrollConfigurationController {
 private final PayrollConfigurationService configuration;
 @PutMapping("/tenant-default") public PayrollJurisdiction tenant(@Valid @RequestBody TenantDefault request) { return configuration.setTenantDefault(request.jurisdiction()); }
 @PostMapping("/employees/{id}/profiles") public ProfileView profile(@PathVariable UUID id,@Valid @RequestBody Profile request) { return configuration.appendProfile(id,request); }
 @PostMapping("/policy-versions") public PayrollPolicy policy(@Valid @RequestBody Policy request) { return configuration.appendPolicy(request); }
}
