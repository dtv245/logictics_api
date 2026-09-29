package com.company.logicstic.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.document.DocumentView;
import com.company.logicstic.service.DocumentService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/documents")
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
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