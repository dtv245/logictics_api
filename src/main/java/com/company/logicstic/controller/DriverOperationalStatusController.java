package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.driverprofile.DriverOperationalStatusView;
import com.company.logicstic.dto.driverprofile.UpdateDriverOperationalStatusRequest;
import com.company.logicstic.service.DriverOperationalStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/{driverId}/operational-status")
public class DriverOperationalStatusController {
    private final DriverOperationalStatusService service;

    public DriverOperationalStatusController(DriverOperationalStatusService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DriverOperationalStatusView>> get(@PathVariable UUID driverId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId), request));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DriverOperationalStatusView>> update(@PathVariable UUID driverId,
            @Valid @RequestBody UpdateDriverOperationalStatusRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, body), request));
    }
}
