package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverInsuranceRequest;
import com.company.logicstic.dto.driverprofile.DriverInsuranceSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverInsuranceView;
import com.company.logicstic.dto.driverprofile.UpdateDriverInsuranceRequest;
import com.company.logicstic.service.DriverInsuranceService;
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
@RequestMapping("/api/drivers/{driverId}/insurances")
public class DriverInsuranceController {
    private final DriverInsuranceService service;

    public DriverInsuranceController(DriverInsuranceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverInsuranceView>>> list(@PathVariable UUID driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "participationStartDate") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{insuranceId}")
    public ResponseEntity<ApiResponse<DriverInsuranceView>> get(@PathVariable UUID driverId,
            @PathVariable UUID insuranceId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, insuranceId), request));
    }

    @GetMapping("/{insuranceId}/sensitive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverInsuranceSensitiveView>> getSensitive(@PathVariable UUID driverId,
            @PathVariable UUID insuranceId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getSensitive(driverId, insuranceId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverInsuranceView>> create(@PathVariable UUID driverId,
            @Valid @RequestBody CreateDriverInsuranceRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{insuranceId}")
    public ResponseEntity<ApiResponse<DriverInsuranceView>> update(@PathVariable UUID driverId,
            @PathVariable UUID insuranceId, @Valid @RequestBody UpdateDriverInsuranceRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, insuranceId, body), request));
    }

    @DeleteMapping("/{insuranceId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID insuranceId) {
        service.deactivate(driverId, insuranceId);
        return ResponseEntity.noContent().build();
    }
}
