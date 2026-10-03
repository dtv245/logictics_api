package com.company.logicstic.controller;

import com.company.logicstic.dto.payroll.CalculateSettlementRequest;
import com.company.logicstic.dto.payroll.DriverSettlementView;
import com.company.logicstic.dto.payroll.SettlementAdjustmentRequest;
import com.company.logicstic.dto.payroll.SettlementReversalRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.payroll.DriverPayEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/driver-settlements") @RequiredArgsConstructor
public class DriverSettlementController {
    private final DriverPayEngine engine;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/calculate") @ResponseStatus(HttpStatus.CREATED)
    public DriverSettlementView calculate(@Valid @RequestBody CalculateSettlementRequest request) {
        return engine.calculate(request.driverId(), request.payPeriodId());
    }

    @GetMapping("/{id}")
    public DriverSettlementView get(@PathVariable UUID id) { return engine.get(id); }

    @PostMapping("/{id}/submit-review")
    public DriverSettlementView submitForReview(@PathVariable UUID id, Authentication authentication) { return engine.transition(id, "IN_REVIEW", actorId(authentication)); }

    @PostMapping("/{id}/approve")
    public DriverSettlementView approve(@PathVariable UUID id, Authentication authentication) { return engine.transition(id, "APPROVED", actorId(authentication)); }

    @PostMapping("/{id}/lock")
    public DriverSettlementView lock(@PathVariable UUID id, Authentication authentication) { return engine.transition(id, "LOCKED", actorId(authentication)); }

    public record ValidationRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason) {}
    @PostMapping("/{id}/require-validation")
    public DriverSettlementView requireValidation(@PathVariable UUID id, @Valid @RequestBody ValidationRequest request, Authentication authentication) {
        return engine.requireValidation(id,request.reason(),actorId(authentication));
    }
    @PostMapping("/{id}/resolve-validation")
    public DriverSettlementView resolveValidation(@PathVariable UUID id, Authentication authentication) {
        return engine.transition(id,"CALCULATED",actorId(authentication));
    }

    @PostMapping("/{id}/adjustments") @ResponseStatus(HttpStatus.CREATED)
    public DriverSettlementView adjustment(@PathVariable UUID id, @Valid @RequestBody SettlementAdjustmentRequest request) {
        return engine.createAdjustment(id, request);
    }

    @PostMapping("/{id}/reversal") @ResponseStatus(HttpStatus.CREATED)
    public DriverSettlementView reversal(@PathVariable UUID id, @Valid @RequestBody SettlementReversalRequest request) {
        return engine.reverse(id, request.reason());
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ForbiddenException("Authenticated payroll actor required");
        Employee actor = employeeRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("Payroll actor must map to an employee record"));
        return actor.getId();
    }
}
