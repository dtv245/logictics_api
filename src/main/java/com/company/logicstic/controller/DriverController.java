package com.company.logicstic.controller;

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

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.employee.CreateEmployeeRequest;
import com.company.logicstic.dto.employee.EmployeeView;
import com.company.logicstic.service.DriverService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeView>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "lastName") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            @RequestParam(required = false) String availability,
            HttpServletRequest request
    ) {
        PagedResponse<EmployeeView> data = driverService.search(search, status, page, pageSize, orderBy, descending, availability);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        EmployeeView data = driverService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeView>> create(
            @Valid @RequestBody CreateEmployeeRequest body,
            HttpServletRequest request
    ) {
        EmployeeView data = driverService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEmployeeRequest body,
            HttpServletRequest request
    ) {
        EmployeeView data = driverService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        driverService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}
