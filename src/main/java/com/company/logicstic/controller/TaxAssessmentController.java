package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.billing.TaxAssessmentService;
import com.company.logicstic.service.billing.domain.TaxAssessment;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/invoices/tax-assessments") @RequiredArgsConstructor
public class TaxAssessmentController {
    private final TaxAssessmentService assessments;
    private final EmployeeRepository employees;
    @PostMapping
    public ResponseEntity<ApiResponse<TaxAssessment>> capture(@jakarta.validation.Valid @RequestBody TaxAssessmentRequest body,
            Authentication auth,HttpServletRequest request) {
        if(auth==null || !auth.isAuthenticated()) throw new ForbiddenException("Authenticated accounting actor required");
        UUID actor=employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Accounting actor must map to tenant employee")).getId();
        return ResponseEntity.ok(ApiResponse.success(assessments.capture(body,actor),request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxAssessment>> get(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(assessments.get(id),request));
    }
}
