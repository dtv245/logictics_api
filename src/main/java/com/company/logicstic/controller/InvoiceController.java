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
import com.company.logicstic.dto.invoice.CreateInvoiceRequest;
import com.company.logicstic.dto.invoice.InvoiceView;
import com.company.logicstic.service.InvoiceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceView>>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "number") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<InvoiceView> data = invoiceService.search(status, type, customerId, employeeId, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        InvoiceView data = invoiceService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceView>> create(
            @Valid @RequestBody CreateInvoiceRequest body,
            HttpServletRequest request
    ) {
        InvoiceView data = invoiceService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateInvoiceRequest body,
            HttpServletRequest request
    ) {
        InvoiceView data = invoiceService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        invoiceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}