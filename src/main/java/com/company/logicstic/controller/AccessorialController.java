package com.company.logicstic.controller;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.accessorial.AccessorialChargeView;
import com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest;
import com.company.logicstic.dto.accessorial.DetentionCalculationRequest;
import com.company.logicstic.dto.accessorial.DetentionCalculationResult;
import com.company.logicstic.service.accessorial.AccessorialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccessorialController {

    private final AccessorialService accessorialService;

    @PostMapping("/loads/{loadId}/accessorials")
    public ResponseEntity<ApiResponse<AccessorialChargeView>> createAccessorial(
            @PathVariable UUID loadId,
            @Valid @RequestBody CreateAccessorialChargeRequest body,
            HttpServletRequest request
    ) {
        AccessorialChargeView data = accessorialService.createAccessorial(loadId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @GetMapping("/loads/{loadId}/accessorials")
    public ResponseEntity<ApiResponse<List<AccessorialChargeView>>> getAccessorials(
            @PathVariable UUID loadId,
            HttpServletRequest request
    ) {
        List<AccessorialChargeView> data = accessorialService.getAccessorialsForLoad(loadId);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/trip-stops/{stopId}/calculate-detention")
    public ResponseEntity<ApiResponse<DetentionCalculationResult>> calculateDetention(
            @PathVariable UUID stopId,
            @Valid @RequestBody DetentionCalculationRequest body,
            HttpServletRequest request
    ) {
        DetentionCalculationResult data = accessorialService.calculateStopDetention(stopId, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PutMapping("/accessorial-charges/{id}/approve")
    public ResponseEntity<ApiResponse<AccessorialChargeView>> approveAccessorial(
            @PathVariable UUID id,
            org.springframework.security.core.Authentication authentication,
            HttpServletRequest request
    ) {
        AccessorialChargeView data = accessorialService.approveAccessorial(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }
}
