package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverlicense.DriverCertificationView;
import com.company.logicstic.dto.driverlicense.DriverHealthCheckView;
import com.company.logicstic.dto.driverlicense.DriverLicenseView;
import com.company.logicstic.service.DriverCertificationService;
import com.company.logicstic.service.DriverHealthCheckService;
import com.company.logicstic.service.DriverLicenseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver/me")
public class DriverSelfCredentialController {
    private final DriverLicenseService licenses;
    private final DriverHealthCheckService healthChecks;
    private final DriverCertificationService certifications;

    public DriverSelfCredentialController(DriverLicenseService licenses, DriverHealthCheckService healthChecks,
            DriverCertificationService certifications) {
        this.licenses = licenses;
        this.healthChecks = healthChecks;
        this.certifications = certifications;
    }

    @GetMapping("/licenses")
    public ResponseEntity<ApiResponse<PagedResponse<DriverLicenseView>>> licenses(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "expiryDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(licenses.listMine(page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/health-checks")
    public ResponseEntity<ApiResponse<PagedResponse<DriverHealthCheckView>>> healthChecks(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "expiryDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(healthChecks.listMine(page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/certifications")
    public ResponseEntity<ApiResponse<PagedResponse<DriverCertificationView>>> certifications(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "expiryDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                certifications.listMine(page, pageSize, orderBy, descending), request));
    }
}
