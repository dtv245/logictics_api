package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverlicense.CreateDriverLicenseRequest;
import com.company.logicstic.dto.driverlicense.DriverLicenseSensitiveView;
import com.company.logicstic.dto.driverlicense.DriverLicenseView;
import com.company.logicstic.dto.driverlicense.UpdateDriverLicenseRequest;
import com.company.logicstic.service.DriverLicenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/{driverId}/licenses")
public class DriverLicenseController {
    private final DriverLicenseService service;

    public DriverLicenseController(DriverLicenseService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverLicenseView>>> list(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "expiryDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{licenseId}")
    public ResponseEntity<ApiResponse<DriverLicenseView>> get(
            @PathVariable UUID driverId, @PathVariable UUID licenseId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, licenseId), request));
    }

    @GetMapping("/{licenseId}/sensitive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverLicenseSensitiveView>> getSensitive(
            @PathVariable UUID driverId, @PathVariable UUID licenseId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getSensitive(driverId, licenseId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverLicenseView>> create(
            @PathVariable UUID driverId, @Valid @RequestBody CreateDriverLicenseRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{licenseId}")
    public ResponseEntity<ApiResponse<DriverLicenseView>> update(
            @PathVariable UUID driverId, @PathVariable UUID licenseId,
            @Valid @RequestBody UpdateDriverLicenseRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, licenseId, body), request));
    }

    @DeleteMapping("/{licenseId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID licenseId) {
        service.deactivate(driverId, licenseId);
        return ResponseEntity.noContent().build();
    }
}
