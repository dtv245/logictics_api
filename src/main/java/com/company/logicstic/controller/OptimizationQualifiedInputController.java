package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.optimization.OptimizationQualifiedInputService;
import com.company.logicstic.service.optimization.OptimizationQualifiedInputService.CaptureRequest;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.Kind;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Captured;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/optimization/qualified-inputs") @RequiredArgsConstructor
public class OptimizationQualifiedInputController {
    private final OptimizationQualifiedInputService inputs;
    private final EmployeeRepository employees;
    @PostMapping
    public ResponseEntity<ApiResponse<Captured>> capture(@RequestBody CaptureRequest body, Authentication auth, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(inputs.capture(body,actor(auth)),request));
    }
    @PostMapping("/forecasts")
    public ResponseEntity<ApiResponse<Captured>> forecast(@RequestBody CaptureRequest body, Authentication auth, HttpServletRequest request) {
        if(body==null || body.kind()!=Kind.FORECAST_COST) throw new BadRequestException("FORECAST_COST_INCOMPLETE","Accounting endpoint captures approved forecasts only");
        return capture(body,auth,request);
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Captured>> get(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inputs.get(id),request));
    }
    private UUID actor(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new ForbiddenException("Authenticated qualified source actor required");
        return employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Source actor must map to tenant employee")).getId();
    }
}
