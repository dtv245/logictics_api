package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.invoice.InvoiceLineView;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.InvoiceLineRepository;
import com.company.logicstic.repository.InvoiceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only invoice lines; rated lines are produced by billing commands, never edited here. */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceLineController {

    private final InvoiceLineRepository lines;
    private final InvoiceRepository invoices;

    @GetMapping("/{id}/lines")
    public ResponseEntity<ApiResponse<List<InvoiceLineView>>> list(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        if (!invoices.existsById(id)) {
            throw new ResourceNotFoundException("Invoice not found: " + id);
        }
        return ResponseEntity.ok(ApiResponse.success(lines.findByInvoiceId(id), request));
    }
}
