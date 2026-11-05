package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverlicense.CreateDriverHealthCheckRequest;
import com.company.logicstic.dto.driverlicense.DriverHealthCheckView;
import com.company.logicstic.dto.driverlicense.UpdateDriverHealthCheckRequest;
import com.company.logicstic.service.DriverHealthCheckService;
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
@RequestMapping("/api/drivers/{driverId}/health-checks")
public class DriverHealthCheckController {
    private final DriverHealthCheckService service;

    public DriverHealthCheckController(DriverHealthCheckService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverHealthCheckView>>> list(
            @PathVariable UUID driverId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "expiryDate") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{checkId}")
    public ResponseEntity<ApiResponse<DriverHealthCheckView>> get(
            @PathVariable UUID driverId, @PathVariable UUID checkId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, checkId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverHealthCheckView>> create(
            @PathVariable UUID driverId, @Valid @RequestBody CreateDriverHealthCheckRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{checkId}")
    public ResponseEntity<ApiResponse<DriverHealthCheckView>> update(
            @PathVariable UUID driverId, @PathVariable UUID checkId,
            @Valid @RequestBody UpdateDriverHealthCheckRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, checkId, body), request));
    }

    @DeleteMapping("/{checkId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID checkId) {
        service.deactivate(driverId, checkId);
        return ResponseEntity.noContent().build();
    }
}
