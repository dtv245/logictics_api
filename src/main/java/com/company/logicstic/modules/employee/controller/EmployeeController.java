package com.company.logicstic.modules.employee.controller;

import com.company.logicstic.modules.employee.dto.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.EmployeeView;
import com.company.logicstic.modules.employee.service.EmployeeService;
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
@RequestMapping("/api/employees")
@Validated
public class EmployeeController {

  private final EmployeeService employeeService;

  public EmployeeController(EmployeeService employeeService) {
    this.employeeService = employeeService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<EmployeeView>>> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID roleId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = "lastName") String orderBy,
      @RequestParam(defaultValue = "false") boolean descending,
      HttpServletRequest request) {
    PagedResponse<EmployeeView> data =
        employeeService.search(search, status, roleId, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<EmployeeView>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    EmployeeView data = employeeService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<EmployeeView>> create(
      @Valid @RequestBody CreateEmployeeRequest body, HttpServletRequest request) {
    EmployeeView data = employeeService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<EmployeeView>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateEmployeeRequest body,
      HttpServletRequest request) {
    EmployeeView data = employeeService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    employeeService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
