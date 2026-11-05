package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverlicense.CreateDriverCertificationRequest;
import com.company.logicstic.dto.driverlicense.DriverCertificationView;
import com.company.logicstic.dto.driverlicense.UpdateDriverCertificationRequest;
import com.company.logicstic.service.DriverCertificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/drivers/{driverId}/certifications")
public class DriverCertificationController {
    private final DriverCertificationService service;

    public DriverCertificationController(DriverCertificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverCertificationView>>> list(
            @PathVariable UUID driverId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "expiryDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{certificationId}")
    public ResponseEntity<ApiResponse<DriverCertificationView>> get(
            @PathVariable UUID driverId, @PathVariable UUID certificationId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, certificationId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverCertificationView>> create(
            @PathVariable UUID driverId, @Valid @RequestBody CreateDriverCertificationRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{certificationId}")
    public ResponseEntity<ApiResponse<DriverCertificationView>> update(
            @PathVariable UUID driverId, @PathVariable UUID certificationId,
            @Valid @RequestBody UpdateDriverCertificationRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, certificationId, body), request));
    }

    @DeleteMapping("/{certificationId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID certificationId) {
        service.deactivate(driverId, certificationId);
        return ResponseEntity.noContent().build();
    }
}
