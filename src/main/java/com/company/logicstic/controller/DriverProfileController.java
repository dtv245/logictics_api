package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.driverprofile.DriverProfileSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverProfileView;
import com.company.logicstic.dto.driverprofile.UpsertDriverProfileRequest;
import com.company.logicstic.service.DriverProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/{driverId}/profile")
public class DriverProfileController {
    private final DriverProfileService service;

    public DriverProfileController(DriverProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DriverProfileView>> get(@PathVariable UUID driverId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId), request));
    }

    @GetMapping("/sensitive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverProfileSensitiveView>> getSensitive(
            @PathVariable UUID driverId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getSensitive(driverId), request));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DriverProfileView>> upsert(@PathVariable UUID driverId,
            @Valid @RequestBody UpsertDriverProfileRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.upsert(driverId, body), request));
    }
}
