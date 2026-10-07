package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.payroll.CalculateSettlementRequest;
import com.company.logicstic.dto.payroll.DriverSettlementView;
import com.company.logicstic.dto.payroll.SettlementAdjustmentRequest;
import com.company.logicstic.dto.payroll.SettlementReversalRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.payroll.DriverPayEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/driver-settlements")
@RequiredArgsConstructor
public class DriverSettlementController {
    private final DriverPayEngine engine;
    private final EmployeeRepository employeeRepository;
    private final com.company.logicstic.service.payroll.SettlementRevenueService revenue;

    @PostMapping("/{id}/recalculate-revenue")
    public ResponseEntity<ApiResponse<DriverSettlementView>> recalculateRevenue(@PathVariable UUID id,
            @Valid @RequestBody com.company.logicstic.service.payroll.SettlementRevenueService.Recalculate body,
            Authentication authentication,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(revenue.recalculate(id,body,actorId(authentication)),request));
    }

    @PostMapping("/{id}/billing-adjustments")
    public ResponseEntity<ApiResponse<com.company.logicstic.service.payroll.SettlementRevenueService.Impact>> billingAdjustment(@PathVariable UUID id,
            @Valid @RequestBody com.company.logicstic.service.payroll.SettlementRevenueService.Adjustment body,
            Authentication authentication,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(revenue.adjust(id,body,actorId(authentication)),request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverSettlementView>>> list(
            @RequestParam(required = false) UUID payPeriodId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String settlementType,
            HttpServletRequest request
    ) {
        List<DriverSettlementView> data = engine.list(payPeriodId, driverId, status, settlementType);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<DriverSettlementView>> calculate(
            @Valid @RequestBody CalculateSettlementRequest body,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.calculate(body.driverId(), body.payPeriodId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverSettlementView>> get(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.get(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/submit-review")
    public ResponseEntity<ApiResponse<DriverSettlementView>> submitForReview(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.transition(id, "IN_REVIEW", actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<DriverSettlementView>> approve(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.transition(id, "APPROVED", actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<DriverSettlementView>> lock(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.transition(id, "LOCKED", actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    public record ValidationRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason) {}

    @PostMapping("/{id}/require-validation")
    public ResponseEntity<ApiResponse<DriverSettlementView>> requireValidation(
            @PathVariable UUID id,
            @Valid @RequestBody ValidationRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.requireValidation(id, body.reason(), actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/resolve-validation")
    public ResponseEntity<ApiResponse<DriverSettlementView>> resolveValidation(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.transition(id, "CALCULATED", actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/adjustments")
    public ResponseEntity<ApiResponse<DriverSettlementView>> adjustment(
            @PathVariable UUID id,
            @Valid @RequestBody SettlementAdjustmentRequest body,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.createAdjustment(id, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/reversal")
    public ResponseEntity<ApiResponse<DriverSettlementView>> reversal(
            @PathVariable UUID id,
            @Valid @RequestBody SettlementReversalRequest body,
            HttpServletRequest request
    ) {
        DriverSettlementView data = engine.reverse(id, body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ForbiddenException("Authenticated payroll actor required");
        Employee actor = employeeRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("Payroll actor must map to an employee record"));
        return actor.getId();
    }
}
