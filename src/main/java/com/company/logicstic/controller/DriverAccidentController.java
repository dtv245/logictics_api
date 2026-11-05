package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverAccidentRequest;
import com.company.logicstic.dto.driverprofile.DriverAccidentView;
import com.company.logicstic.dto.driverprofile.UpdateDriverAccidentRequest;
import com.company.logicstic.service.DriverAccidentService;
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
@RequestMapping("/api/drivers/{driverId}/accidents")
public class DriverAccidentController {
    private final DriverAccidentService service;

    public DriverAccidentController(DriverAccidentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverAccidentView>>> list(
            @PathVariable UUID driverId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "accidentDateTime") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(driverId, page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/{accidentId}")
    public ResponseEntity<ApiResponse<DriverAccidentView>> get(
            @PathVariable UUID driverId, @PathVariable UUID accidentId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.get(driverId, accidentId), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverAccidentView>> create(
            @PathVariable UUID driverId, @Valid @RequestBody CreateDriverAccidentRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(driverId, body), request));
    }

    @PutMapping("/{accidentId}")
    public ResponseEntity<ApiResponse<DriverAccidentView>> update(
            @PathVariable UUID driverId, @PathVariable UUID accidentId,
            @Valid @RequestBody UpdateDriverAccidentRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(driverId, accidentId, body), request));
    }

    @DeleteMapping("/{accidentId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID driverId, @PathVariable UUID accidentId) {
        service.cancel(driverId, accidentId);
        return ResponseEntity.noContent().build();
    }
}
