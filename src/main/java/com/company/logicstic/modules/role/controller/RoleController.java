package com.company.logicstic.modules.role.controller;

import com.company.logicstic.modules.role.dto.CreateRoleRequest;
import com.company.logicstic.modules.role.dto.RoleView;
import com.company.logicstic.modules.role.service.RoleService;
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
@RequestMapping("/api/roles")
@Validated
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<RoleView>>> list(
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      HttpServletRequest request) {
    PagedResponse<RoleView> data = roleService.list(page, pageSize);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleView>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    RoleView data = roleService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<RoleView>> create(
      @Valid @RequestBody CreateRoleRequest body, HttpServletRequest request) {
    RoleView data = roleService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleView>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateRoleRequest body,
      HttpServletRequest request) {
    RoleView data = roleService.update(id, body);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    roleService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
