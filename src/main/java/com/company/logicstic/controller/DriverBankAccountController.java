package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverBankAccountRequest;
import com.company.logicstic.dto.driverprofile.DriverBankAccountSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverBankAccountView;
import com.company.logicstic.dto.driverprofile.UpdateDriverBankAccountRequest;
import com.company.logicstic.service.DriverBankAccountService;
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
@RequestMapping("/api/drivers/{driverId}/bank-accounts")
public class DriverBankAccountController {
    private final DriverBankAccountService service;

    public DriverBankAccountController(DriverBankAccountService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverBankAccountView>>> list(@PathVariable UUID driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<DriverBankAccountView>> get(@PathVariable UUID driverId,
            @PathVariable UUID accountId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, accountId), request));
    }

    @GetMapping("/{accountId}/sensitive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverBankAccountSensitiveView>> getSensitive(@PathVariable UUID driverId,
            @PathVariable UUID accountId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getSensitive(driverId, accountId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverBankAccountView>> create(@PathVariable UUID driverId,
            @Valid @RequestBody CreateDriverBankAccountRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<ApiResponse<DriverBankAccountView>> update(@PathVariable UUID driverId,
            @PathVariable UUID accountId, @Valid @RequestBody UpdateDriverBankAccountRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, accountId, body), request));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID accountId) {
        service.deactivate(driverId, accountId);
        return ResponseEntity.noContent().build();
    }
}
