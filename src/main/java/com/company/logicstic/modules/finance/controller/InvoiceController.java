package com.company.logicstic.modules.finance.controller;

import com.company.logicstic.modules.finance.dto.CreateInvoiceRequest;
import com.company.logicstic.modules.finance.dto.InvoiceView;
import com.company.logicstic.modules.finance.service.InvoiceService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!nodb")
@RestController
@RequestMapping("/api/invoices")
@Validated
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
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_FIELD_NUMBER) String orderBy,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_DIRECTION) boolean descending,
      HttpServletRequest request) {
    PagedResponse<InvoiceView> data =
        invoiceService.search(
            status, type, customerId, employeeId, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<InvoiceView>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    InvoiceView data = invoiceService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<InvoiceView>> create(
      @Valid @RequestBody CreateInvoiceRequest body, HttpServletRequest request) {
    InvoiceView data = invoiceService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<InvoiceView>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateInvoiceRequest body,
      HttpServletRequest request) {
    InvoiceView data = invoiceService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    invoiceService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
