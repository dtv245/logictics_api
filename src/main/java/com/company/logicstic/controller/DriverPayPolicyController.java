package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.payroll.DriverPayPolicyRequest;
import com.company.logicstic.dto.payroll.DriverPayPolicyView;
import com.company.logicstic.service.payroll.DriverPayPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/driver-pay-policies")
@RequiredArgsConstructor
public class DriverPayPolicyController {
    private final DriverPayPolicyService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverPayPolicyView>>> list(HttpServletRequest request) {
        List<DriverPayPolicyView> data = service.list().stream().map(DriverPayPolicyView::from).toList();
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverPayPolicyView>> get(@PathVariable UUID id, HttpServletRequest request) {
        DriverPayPolicyView data = DriverPayPolicyView.from(service.get(id));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverPayPolicyView>> create(
            @Valid @RequestBody DriverPayPolicyRequest body,
            HttpServletRequest request
    ) {
        DriverPayPolicyView data = DriverPayPolicyView.from(service.create(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PostMapping("/{id}/new-version")
    public ResponseEntity<ApiResponse<DriverPayPolicyView>> newVersion(
            @PathVariable UUID id,
            @Valid @RequestBody DriverPayPolicyRequest body,
            HttpServletRequest request
    ) {
        DriverPayPolicyView data = DriverPayPolicyView.from(service.newVersion(id, body));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }
}
