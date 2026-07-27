package com.company.logicstic.modules.fleet.controller;

import com.company.logicstic.modules.fleet.dto.request.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.response.TruckResponse;
import com.company.logicstic.modules.fleet.service.TruckService;
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
@RequestMapping("/api/trucks")
@Validated
public class TruckController {

  private final TruckService truckService;

  public TruckController(TruckService truckService) {
    this.truckService = truckService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<TruckResponse>>> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_FIELD_NUMBER) String orderBy,
      @RequestParam(defaultValue = "false") boolean descending,
      HttpServletRequest request) {
    PagedResponse<TruckResponse> data =
        truckService.search(search, status, type, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<TruckResponse>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    TruckResponse data = truckService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<TruckResponse>> create(
      @Valid @RequestBody CreateTruckRequest body, HttpServletRequest request) {
    TruckResponse data = truckService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<TruckResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateTruckRequest body,
      HttpServletRequest request) {
    TruckResponse data = truckService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    truckService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
