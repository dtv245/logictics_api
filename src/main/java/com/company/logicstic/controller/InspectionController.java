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
import com.company.logicstic.dto.inspection.CreateInspectionRequest;
import com.company.logicstic.dto.inspection.InspectionView;
import com.company.logicstic.service.InspectionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<InspectionView>>> search(
            @RequestParam(required = false) UUID loadId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "inspectedAt") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<InspectionView> data = inspectionService.search(loadId, type, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InspectionView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        InspectionView data = inspectionService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InspectionView>> create(
            @Valid @RequestBody CreateInspectionRequest body,
            HttpServletRequest request
    ) {
        InspectionView data = inspectionService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InspectionView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateInspectionRequest body,
            HttpServletRequest request
    ) {
        InspectionView data = inspectionService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        inspectionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}