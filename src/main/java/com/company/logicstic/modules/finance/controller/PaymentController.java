package com.company.logicstic.modules.finance.controller;

import com.company.logicstic.modules.finance.dto.CreatePaymentRequest;
import com.company.logicstic.modules.finance.dto.PaymentView;
import com.company.logicstic.modules.finance.service.PaymentService;
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
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<PaymentView>>> search(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID invoiceId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = "recordedAt") String orderBy,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_DIRECTION) boolean descending,
      HttpServletRequest request) {
    PagedResponse<PaymentView> data =
        paymentService.search(status, invoiceId, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PaymentView>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    PaymentView data = paymentService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<PaymentView>> create(
      @Valid @RequestBody CreatePaymentRequest body, HttpServletRequest request) {
    PaymentView data = paymentService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PaymentView>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreatePaymentRequest body,
      HttpServletRequest request) {
    PaymentView data = paymentService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    paymentService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
