package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverDependentRequest;
import com.company.logicstic.dto.driverprofile.DriverDependentSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverDependentView;
import com.company.logicstic.dto.driverprofile.UpdateDriverDependentRequest;
import com.company.logicstic.service.DriverDependentService;
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
@RequestMapping("/api/drivers/{driverId}/dependents")
public class DriverDependentController {
    private final DriverDependentService service;

    public DriverDependentController(DriverDependentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverDependentView>>> list(@PathVariable UUID driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "fullName") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{dependentId}")
    public ResponseEntity<ApiResponse<DriverDependentView>> get(@PathVariable UUID driverId,
            @PathVariable UUID dependentId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, dependentId), request));
    }

    @GetMapping("/{dependentId}/sensitive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverDependentSensitiveView>> getSensitive(@PathVariable UUID driverId,
            @PathVariable UUID dependentId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getSensitive(driverId, dependentId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverDependentView>> create(@PathVariable UUID driverId,
            @Valid @RequestBody CreateDriverDependentRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{dependentId}")
    public ResponseEntity<ApiResponse<DriverDependentView>> update(@PathVariable UUID driverId,
            @PathVariable UUID dependentId, @Valid @RequestBody UpdateDriverDependentRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, dependentId, body), request));
    }

    @DeleteMapping("/{dependentId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID dependentId) {
        service.deactivate(driverId, dependentId);
        return ResponseEntity.noContent().build();
    }
}
