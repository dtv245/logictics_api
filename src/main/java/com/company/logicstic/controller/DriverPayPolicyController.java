package com.company.logicstic.controller;

import com.company.logicstic.dto.payroll.DriverPayPolicyRequest;
import com.company.logicstic.dto.payroll.DriverPayPolicyView;
import com.company.logicstic.service.payroll.DriverPayPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/driver-pay-policies") @RequiredArgsConstructor
public class DriverPayPolicyController {
    private final DriverPayPolicyService service;
    @GetMapping public List<DriverPayPolicyView> list() { return service.list().stream().map(DriverPayPolicyView::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public DriverPayPolicyView create(@Valid @RequestBody DriverPayPolicyRequest request) { return DriverPayPolicyView.from(service.create(request)); }
    @PostMapping("/{id}/new-version") @ResponseStatus(HttpStatus.CREATED)
    public DriverPayPolicyView newVersion(@PathVariable UUID id, @Valid @RequestBody DriverPayPolicyRequest request) { return DriverPayPolicyView.from(service.newVersion(id, request)); }
}
