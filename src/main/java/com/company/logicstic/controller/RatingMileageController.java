package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.rating.ContractMileageRequest;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.rating.RatingMileageService;
import com.company.logicstic.service.rating.domain.ContractMileageEvidence;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class RatingMileageController {
    private final RatingMileageService mileage;
    private final EmployeeRepository employees;
    @PostMapping("/api/loads/{id}/rating/contract-mileage")
    public ResponseEntity<ApiResponse<ContractMileageEvidence>> capture(@PathVariable UUID id,
            @RequestBody ContractMileageRequest body, Authentication auth, HttpServletRequest request) {
        if (auth == null || !auth.isAuthenticated()) throw new ForbiddenException("Authenticated agreement actor required");
        var actor = employees.findByEmail(auth.getName()).orElseThrow(() -> new ForbiddenException("Agreement actor must map to tenant employee"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mileage.capture(id, body, actor.getId()), request));
    }
}
