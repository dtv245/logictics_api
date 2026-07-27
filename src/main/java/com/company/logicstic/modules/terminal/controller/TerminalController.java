package com.company.logicstic.modules.terminal.controller;

import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.modules.terminal.service.TerminalService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * HTTP boundary for the terminal feature — the UN/LOCODE directory of sea ports, rail terminals,
 * depots, air cargo facilities and border crossings ({@code docs/docs/business-spec.md} §2.4).
 *
 * <p>A controller does four things and nothing else: bind, validate, delegate, wrap. No business
 * rules, no repository access, no {@code @Transactional}, no try/catch — failures travel to {@code
 * GlobalExceptionHandler}.
 *
 * <p>Authorisation is declared per endpoint with {@code @PreAuthorize} and mirrored by the path
 * matchers in {@code SecurityConfiguration}: the filter chain is the coarse gate, the annotation is
 * the specific rule that survives a path rename. Keep the two in sync.
 */
@Profile("!nodb")
@RestController
@RequestMapping("/api/terminals")
@RequiredArgsConstructor
@Validated
public class TerminalController {

  private final TerminalService terminalService;

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER','DISPATCHER','DRIVER')")
  public ResponseEntity<ApiResponse<PagedResponse<TerminalResponse>>> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) TerminalType type,
      @RequestParam(required = false) String countryCode,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = Constants.DEFAULT_SORT_FIELD_NAME) String orderBy,
      @RequestParam(defaultValue = "false") boolean descending,
      HttpServletRequest request) {
    PagedResponse<TerminalResponse> data =
        terminalService.search(search, type, countryCode, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER','DISPATCHER','DRIVER')")
  public ResponseEntity<ApiResponse<TerminalResponse>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    return ResponseEntity.ok(ApiResponse.success(terminalService.getById(id), request));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER','DISPATCHER')")
  public ResponseEntity<ApiResponse<TerminalResponse>> create(
      @Valid @RequestBody CreateTerminalRequest body, HttpServletRequest request) {
    TerminalResponse data = terminalService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER','DISPATCHER')")
  public ResponseEntity<ApiResponse<TerminalResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateTerminalRequest body,
      HttpServletRequest request) {
    return ResponseEntity.ok(ApiResponse.success(terminalService.update(id, body), request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    terminalService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
  }
}
