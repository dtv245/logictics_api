package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.driverprofile.DriverOperationalStatusView;
import com.company.logicstic.service.DriverOperationalStatusService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver/me/operational-status")
public class DriverSelfOperationalStatusController {
    private final DriverOperationalStatusService service;

    public DriverSelfOperationalStatusController(DriverOperationalStatusService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DriverOperationalStatusView>> get(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getMine(), request));
    }
}
