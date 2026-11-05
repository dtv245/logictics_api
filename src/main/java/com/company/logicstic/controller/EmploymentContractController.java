package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateEmploymentContractRequest;
import com.company.logicstic.dto.driverprofile.EmploymentContractView;
import com.company.logicstic.dto.driverprofile.UpdateEmploymentContractRequest;
import com.company.logicstic.service.EmploymentContractService;
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
@RequestMapping("/api/drivers/{driverId}/contracts")
public class EmploymentContractController {
    private final EmploymentContractService service;

    public EmploymentContractController(EmploymentContractService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EmploymentContractView>>> list(@PathVariable UUID driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "effectiveDate") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ApiResponse<EmploymentContractView>> get(@PathVariable UUID driverId,
            @PathVariable UUID contractId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, contractId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmploymentContractView>> create(@PathVariable UUID driverId,
            @Valid @RequestBody CreateEmploymentContractRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{contractId}")
    public ResponseEntity<ApiResponse<EmploymentContractView>> update(@PathVariable UUID driverId,
            @PathVariable UUID contractId, @Valid @RequestBody UpdateEmploymentContractRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, contractId, body), request));
    }

    @DeleteMapping("/{contractId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID contractId) {
        service.deactivate(driverId, contractId);
        return ResponseEntity.noContent().build();
    }
}
