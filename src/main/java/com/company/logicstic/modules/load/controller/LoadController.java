package com.company.logicstic.modules.load.controller;

import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.service.LoadService;
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
@RequestMapping("/api/loads")
@Validated
public class LoadController {

  private final LoadService loadService;

  public LoadController(LoadService loadService) {
    this.loadService = loadService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<LoadResponse>>> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID truckId,
      @RequestParam(required = false) UUID dispatcherId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_FIELD_NAME) String orderBy,
      @RequestParam(defaultValue = "false") boolean descending,
      HttpServletRequest request) {
    PagedResponse<LoadResponse> data =
        loadService.search(
            search, status, customerId, truckId, dispatcherId, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<LoadResponse>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    LoadResponse data = loadService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<LoadResponse>> create(
      @Valid @RequestBody CreateLoadRequest body, HttpServletRequest request) {
    LoadResponse data = loadService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<LoadResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateLoadRequest body,
      HttpServletRequest request) {
    LoadResponse data = loadService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    loadService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }

  // ── State machine transitions ──────────────────────────────────────

  @PostMapping("/{id}/dispatch")
  public ResponseEntity<ApiResponse<LoadResponse>> dispatch(
      @PathVariable UUID id, HttpServletRequest request) {
    LoadResponse data = loadService.dispatch(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping("/{id}/pick-up")
  public ResponseEntity<ApiResponse<LoadResponse>> pickUp(
      @PathVariable UUID id, HttpServletRequest request) {
    LoadResponse data = loadService.pickUp(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping("/{id}/deliver")
  public ResponseEntity<ApiResponse<LoadResponse>> deliver(
      @PathVariable UUID id, HttpServletRequest request) {
    LoadResponse data = loadService.deliver(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<LoadResponse>> cancel(
      @PathVariable UUID id, HttpServletRequest request) {
    LoadResponse data = loadService.cancel(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }
}
