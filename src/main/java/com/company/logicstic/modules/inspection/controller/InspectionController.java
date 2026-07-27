package com.company.logicstic.modules.inspection.controller;

import com.company.logicstic.modules.inspection.dto.request.CreateInspectionRequest;
import com.company.logicstic.modules.inspection.dto.response.InspectionResponse;
import com.company.logicstic.modules.inspection.service.InspectionService;
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
@RequestMapping("/api/inspections")
@Validated
public class InspectionController {

  private final InspectionService inspectionService;

  public InspectionController(InspectionService inspectionService) {
    this.inspectionService = inspectionService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<InspectionResponse>>> search(
      @RequestParam(required = false) UUID loadId,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = "inspectedAt") String orderBy,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_DIRECTION) boolean descending,
      HttpServletRequest request) {
    PagedResponse<InspectionResponse> data =
        inspectionService.search(loadId, type, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<InspectionResponse>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    InspectionResponse data = inspectionService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<InspectionResponse>> create(
      @Valid @RequestBody CreateInspectionRequest body, HttpServletRequest request) {
    InspectionResponse data = inspectionService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<InspectionResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateInspectionRequest body,
      HttpServletRequest request) {
    InspectionResponse data = inspectionService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    inspectionService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
