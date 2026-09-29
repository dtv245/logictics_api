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
import com.company.logicstic.dto.truck.CreateTruckRequest;
import com.company.logicstic.dto.truck.TruckView;
import com.company.logicstic.service.TruckService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/trucks")
public class TruckController {

    private final TruckService truckService;

    public TruckController(TruckService truckService) {
        this.truckService = truckService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TruckView>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "number") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<TruckView> data = truckService.search(search, status, type, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TruckView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        TruckView data = truckService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TruckView>> create(
            @Valid @RequestBody CreateTruckRequest body,
            HttpServletRequest request
    ) {
        TruckView data = truckService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TruckView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTruckRequest body,
            HttpServletRequest request
    ) {
        TruckView data = truckService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        truckService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}