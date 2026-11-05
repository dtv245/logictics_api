package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverEmergencyContactRequest;
import com.company.logicstic.dto.driverprofile.DriverEmergencyContactView;
import com.company.logicstic.dto.driverprofile.UpdateDriverEmergencyContactRequest;
import com.company.logicstic.service.DriverEmergencyContactService;
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
@RequestMapping("/api/drivers/{driverId}/emergency-contacts")
public class DriverEmergencyContactController {
    private final DriverEmergencyContactService service;

    public DriverEmergencyContactController(DriverEmergencyContactService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverEmergencyContactView>>> list(@PathVariable UUID driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{contactId}")
    public ResponseEntity<ApiResponse<DriverEmergencyContactView>> get(@PathVariable UUID driverId,
            @PathVariable UUID contactId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, contactId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverEmergencyContactView>> create(@PathVariable UUID driverId,
            @Valid @RequestBody CreateDriverEmergencyContactRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{contactId}")
    public ResponseEntity<ApiResponse<DriverEmergencyContactView>> update(@PathVariable UUID driverId,
            @PathVariable UUID contactId, @Valid @RequestBody UpdateDriverEmergencyContactRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, contactId, body), request));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID driverId, @PathVariable UUID contactId) {
        service.deactivate(driverId, contactId);
        return ResponseEntity.noContent().build();
    }
}
