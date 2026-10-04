package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.optimization.OptimizationApplicationService;
import com.company.logicstic.service.optimization.OptimizationApplicationService.CreateRequest;
import com.company.logicstic.service.optimization.OptimizationAcceptanceService;
import com.company.logicstic.service.optimization.OptimizationAcceptanceService.AcceptRequest;
import com.company.logicstic.repository.OptimizationAcceptanceRepository.Accepted;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.Outcome;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/optimization/runs") @RequiredArgsConstructor
public class OptimizationRunController {
    private final OptimizationApplicationService optimization;
    private final OptimizationAcceptanceService acceptances;
    private final EmployeeRepository employees;
    @PostMapping
    public ResponseEntity<ApiResponse<Outcome>> create(@RequestBody CreateRequest body,Authentication auth,HttpServletRequest request) {
        if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))throw new ForbiddenException("Authenticated optimization actor required");
        UUID actor=employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Optimization actor must map to tenant employee")).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(optimization.create(body,actor,request.getHeader("X-Correlation-Id")),request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Outcome>> get(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(optimization.get(id),request));
    }
    @PostMapping("/{id}/assignments/{candidateId}/accept")
    public ResponseEntity<ApiResponse<Accepted>> accept(@PathVariable UUID id,@PathVariable UUID candidateId,@RequestBody AcceptRequest body,Authentication auth,HttpServletRequest request) {
        if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))throw new ForbiddenException("Authenticated acceptance actor required");
        UUID actor=employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Acceptance actor must map to tenant employee")).getId();
        return ResponseEntity.ok(ApiResponse.success(acceptances.accept(id,candidateId,body,actor),request));
    }
}
