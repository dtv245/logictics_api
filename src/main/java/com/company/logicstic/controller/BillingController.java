package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.invoice.GenerateInvoiceRequest;
import com.company.logicstic.dto.invoice.BillingCorrectionRequests;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.billing.BillingService;
import com.company.logicstic.service.billing.domain.BillingInvoice;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/invoices/billing") @RequiredArgsConstructor
public class BillingController {
    private final BillingService billing;
    private final EmployeeRepository employees;
    private UUID actor(Authentication auth) {
        if(auth==null || !auth.isAuthenticated()) throw new ForbiddenException("Authenticated Accounting actor required");
        return employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Billing actor must map to tenant employee")).getId();
    }
    @PostMapping("/primary")
    public ResponseEntity<ApiResponse<BillingInvoice>> primary(@RequestBody GenerateInvoiceRequest body,Authentication auth,HttpServletRequest request) {
        String correlation=(String)request.getAttribute("correlationId");if(correlation==null)correlation=UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.success(billing.generatePrimary(body,actor(auth),correlation),request));
    }
    public record Regenerate(UUID expectedSnapshotId,GenerateInvoiceRequest generation) { }
    public record Issue(String idempotencyKey) { }
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<ApiResponse<BillingInvoice>> regenerate(@PathVariable UUID id,@RequestBody Regenerate body,Authentication auth,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(billing.regenerateDraft(id,body.expectedSnapshotId(),body.generation(),actor(auth)),request));
    }
    @PostMapping("/{id}/issue")
    public ResponseEntity<ApiResponse<BillingInvoice>> issue(@PathVariable UUID id,@RequestBody Issue body,Authentication auth,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(billing.issue(id,body.idempotencyKey(),actor(auth)),request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BillingInvoice>> get(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(billing.get(id),request));
    }
    @PostMapping("/{id}/supplemental")
    public ResponseEntity<ApiResponse<BillingInvoice>> supplemental(@PathVariable UUID id,@RequestBody BillingCorrectionRequests.Supplemental body,Authentication auth,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(billing.supplemental(id,body,actor(auth)),request));
    }
    @PostMapping("/{id}/credit")
    public ResponseEntity<ApiResponse<BillingInvoice>> credit(@PathVariable UUID id,@RequestBody BillingCorrectionRequests.Credit body,Authentication auth,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(billing.credit(id,body,actor(auth)),request));
    }
    @PostMapping("/{id}/rebill")
    public ResponseEntity<ApiResponse<BillingInvoice>> rebill(@PathVariable UUID id,@RequestBody BillingCorrectionRequests.Rebill body,Authentication auth,HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(billing.rebill(id,body,actor(auth)),request));
    }
}
