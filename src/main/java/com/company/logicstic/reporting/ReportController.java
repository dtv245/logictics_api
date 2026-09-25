package com.company.logicstic.reporting;

import com.company.logicstic.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for the reporting feature — the six aggregates behind the executive screen.
 *
 * <p>A controller does four things and nothing else: bind, validate, delegate, wrap. No business
 * rules, no repository access, no {@code @Transactional}, no try/catch — failures travel to {@code
 * GlobalExceptionHandler}.
 *
 * <p>Authorisation is declared per endpoint with {@code @PreAuthorize} and mirrored by the path
 * matchers in {@code SecurityConfiguration}: the filter chain is the coarse gate, the annotation is
 * the specific rule that survives a path rename. Keep the two in sync.
 *
 * <p><b>{@code currency} is required on every endpoint and has no default.</b> Amounts are stored
 * per row in {@code invoices.total_currency} and {@code payments.amount_currency} — a column with
 * no default and no constraint — so summing across them is meaningless. A server-side default would
 * not be a safety net either: it would be a unit chosen on the caller's behalf and then baked into
 * a total. The pattern is validated here so a malformed code is a 400 at the boundary rather than a
 * silently empty result from a query that matched no rows.
 *
 * <p>Every response echoes back the currency it was computed in and carries a {@code completeness}
 * block, so a caller can always say which unit it is reading and what the aggregate actually found.
 */
@Profile("!nodb")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

  private final ReportService reportService;

  @GetMapping("/executive-summary")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<ExecutiveSummaryResponse>> executiveSummary(
      @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      HttpServletRequest request) {
    ExecutiveSummaryResponse data = reportService.executiveSummary(currency, from, to);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/financials/monthly")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<MonthlyFinancialsResponse>> monthlyFinancials(
      @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      HttpServletRequest request) {
    MonthlyFinancialsResponse data = reportService.monthlyFinancials(currency, from, to);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/costs/by-category")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<CostBreakdownResponse>> costsByCategory(
      @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      HttpServletRequest request) {
    CostBreakdownResponse data = reportService.costsByCategory(currency, from, to);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/fleet/health")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<FleetHealthResponse>> fleetHealth(
      @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      HttpServletRequest request) {
    FleetHealthResponse data = reportService.fleetHealth(currency, from, to);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/customers/concentration")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<CustomerConcentrationResponse>> customerConcentration(
      @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    CustomerConcentrationResponse data =
        reportService.customerConcentration(currency, from, to, limit);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/receivables/aging")
  @PreAuthorize("hasAnyRole('SUPERADMIN','OWNER','MANAGER')")
  public ResponseEntity<ApiResponse<ReceivablesAgingResponse>> receivablesAging(
      @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @RequestParam(required = false) OffsetDateTime asOf,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      HttpServletRequest request) {
    ReceivablesAgingResponse data = reportService.receivablesAging(currency, asOf, from, to);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }
}
