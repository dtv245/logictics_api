package com.company.logicstic.controller;
import java.util.UUID;
import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.accessorial.AccessorialChargeView;
import com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest;
import com.company.logicstic.service.AccessorialChargeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// Retained compatibility adapter; AccessorialController owns the canonical HTTP routes.
@Deprecated @RequiredArgsConstructor
public class AccessorialChargeController {
    private final AccessorialChargeService service;
    @PostMapping("/api/loads/{loadId}/accessorials")
    public ResponseEntity<ApiResponse<AccessorialChargeView>> create(@PathVariable UUID loadId, @Valid @RequestBody CreateAccessorialChargeRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(loadId, body), request));
    }
    @PutMapping("/api/accessorial-charges/{id}/approve")
    public ResponseEntity<ApiResponse<AccessorialChargeView>> approve(@PathVariable UUID id, @RequestParam(required = false) UUID actorId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.approve(id, actorId), request));
    }
}
