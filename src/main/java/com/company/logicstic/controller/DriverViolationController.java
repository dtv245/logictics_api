package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverViolationRequest;
import com.company.logicstic.dto.driverprofile.DriverViolationView;
import com.company.logicstic.dto.driverprofile.UpdateDriverViolationRequest;
import com.company.logicstic.service.DriverViolationService;
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
@RequestMapping("/api/drivers/{driverId}/violations")
public class DriverViolationController {
    private final DriverViolationService service;

    public DriverViolationController(DriverViolationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverViolationView>>> list(
            @PathVariable UUID driverId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "violationDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{violationId}")
    public ResponseEntity<ApiResponse<DriverViolationView>> get(
            @PathVariable UUID driverId, @PathVariable UUID violationId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, violationId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverViolationView>> create(
            @PathVariable UUID driverId, @Valid @RequestBody CreateDriverViolationRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{violationId}")
    public ResponseEntity<ApiResponse<DriverViolationView>> update(
            @PathVariable UUID driverId, @PathVariable UUID violationId,
            @Valid @RequestBody UpdateDriverViolationRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, violationId, body), request));
    }

    @DeleteMapping("/{violationId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID violationId) {
        service.deactivate(driverId, violationId);
        return ResponseEntity.noContent().build();
    }
}
