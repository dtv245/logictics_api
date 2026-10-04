package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.rating.RateRuleRequest;
import com.company.logicstic.dto.rating.RatingContractRequest;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.rating.RatePolicyService;
import com.company.logicstic.service.rating.domain.RateRule;
import com.company.logicstic.service.rating.domain.RatingContract;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Authoring only: never calculates or accepts a financial rating. */
@RestController @RequestMapping("/api/rating") @RequiredArgsConstructor
public class RatePolicyController {
    private final RatePolicyService policies;
    private final EmployeeRepository employees;

    @PostMapping("/contracts")
    public ResponseEntity<ApiResponse<RatingContract>> createContract(@RequestBody RatingContractRequest body,
            Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policies.createContract(body, actor(authentication)), request));
    }
    @PostMapping("/contracts/{id}/versions")
    public ResponseEntity<ApiResponse<RatingContract>> contractVersion(@PathVariable UUID id, @RequestParam int expectedVersion,
            @RequestBody RatingContractRequest body, Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policies.newContractVersion(id, expectedVersion, body, actor(authentication)), request));
    }
    @GetMapping("/contracts/{id}/versions/{version}")
    public ResponseEntity<ApiResponse<RatingContract>> getContract(@PathVariable UUID id, @PathVariable int version, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(policies.getContract(id, version), request));
    }
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<RateRule>> createRule(@RequestBody RateRuleRequest body,
            Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policies.createRule(body, actor(authentication)), request));
    }
    @PostMapping("/rules/{id}/versions")
    public ResponseEntity<ApiResponse<RateRule>> ruleVersion(@PathVariable UUID id, @RequestParam int expectedVersion,
            @RequestBody RateRuleRequest body, Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policies.newRuleVersion(id, expectedVersion, body, actor(authentication)), request));
    }
    @GetMapping("/rules/{id}/versions/{version}")
    public ResponseEntity<ApiResponse<RateRule>> getRule(@PathVariable UUID id, @PathVariable int version, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(policies.getRule(id, version), request));
    }
    private UUID actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ForbiddenException("Authenticated rating policy actor required");
        return employees.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("Rating actor must map to a tenant employee")).getId();
    }
}
