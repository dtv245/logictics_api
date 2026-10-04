package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.optimization.OptimizationPolicyService;
import com.company.logicstic.service.optimization.OptimizationPolicyService.PublishRequest;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.PublishedPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/optimization/policies") @RequiredArgsConstructor
public class OptimizationPolicyController {
    private final OptimizationPolicyService policies;
    private final EmployeeRepository employees;
    @PostMapping
    public ResponseEntity<ApiResponse<PublishedPolicy>> publish(@RequestBody PublishRequest body, Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) throw new ForbiddenException("Authenticated optimization policy actor required");
        UUID actor = employees.findByEmail(authentication.getName()).orElseThrow(() -> new ForbiddenException("Optimization actor must map to tenant employee")).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policies.publish(body, actor), request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublishedPolicy>> get(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(policies.get(id), request));
    }
}
