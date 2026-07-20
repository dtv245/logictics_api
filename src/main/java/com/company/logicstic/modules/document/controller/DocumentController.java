package com.company.logicstic.modules.document.controller;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.modules.document.dto.DocumentView;
import com.company.logicstic.modules.document.service.DocumentService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Profile("!nodb")
@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DocumentView>>> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID loadId,
            @RequestParam(required = false) UUID truckId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
            @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE) @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize,
            @RequestParam(defaultValue = "fileName") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<DocumentView> data = documentService.search(type, status, loadId, truckId, employeeId, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        DocumentView data = documentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        documentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}