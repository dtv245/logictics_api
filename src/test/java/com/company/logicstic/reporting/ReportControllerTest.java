package com.company.logicstic.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.security.RestAccessDeniedHandler;
import com.company.logicstic.security.RestAuthenticationEntryPoint;
import com.company.logicstic.security.SecurityConfiguration;
import com.company.logicstic.shared.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller slice test for {@link ReportController}.
 *
 * <p>Reference for {@code docs/docs/development/engineering-conventions.md} §15.3: this layer
 * asserts HTTP status codes, the response envelope, Bean Validation wiring, method-level
 * authorisation and error translation — never business rules, which are unit-tested against the
 * service.
 *
 * <p>{@code @WebMvcTest} loads no {@code @Configuration} of its own, so the production {@link
 * SecurityConfiguration} is imported explicitly together with the two security auto-configurations
 * that supply the {@code HttpSecurity} builder. Without them the slice would run with no filter
 * chain and happily return 200 for an anonymous request — a security test that always passes. With
 * them, the 401/403 cases below exercise the same path matchers and {@code @PreAuthorize} rules as
 * production. Both a negative and a positive case are present: three refusals prove the gate
 * closes, and the manager case proves it opens.
 *
 * <p>{@code @ActiveProfiles("test")} is mandatory, not decorative: {@code application.yml} imports
 * {@code .env}, and {@code .env.example} ships {@code SPRING_PROFILES_ACTIVE=nodb}. Without an
 * explicit profile the slice inherits {@code nodb}, every {@code @Profile("!nodb")} controller is
 * excluded from the context, and each request silently resolves to the static-resource handler with
 * a 404 instead of failing loudly.
 */
@WebMvcTest(ReportController.class)
@ImportAutoConfiguration({
  SecurityAutoConfiguration.class,
  ServletWebSecurityAutoConfiguration.class
})
@Import({
  SecurityConfiguration.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.security.jwt.issuer=https://issuer.example",
      "app.security.jwt.audience=logisticsx.api",
      "app.security.jwt.jwk-set-uri=https://issuer.example/jwks"
    })
@DisplayName("/api/reports")
class ReportControllerTest {

  private static final String MANAGER = "ROLE_MANAGER";
  private static final String DISPATCHER = "ROLE_DISPATCHER";
  private static final String DRIVER = "ROLE_DRIVER";

  private static final String SUMMARY = "/api/reports/executive-summary";

  private final MockMvc mockMvc;

  @MockitoBean private ReportService reportService;

  ReportControllerTest(@Autowired MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  // ── the rule this whole layer exists to carry ────────────────────────────────────────────────

  @Test
  @DisplayName("serialises an unavailable metric as a null value with available false")
  void should_serialise_unavailable_metric_as_null_value_and_available_false() throws Exception {
    // given a fleet whose size is known and whose utilisation cannot be measured
    given(reportService.executiveSummary(any(), any(), any())).willReturn(summary());

    // when / then
    mockMvc
        .perform(get(SUMMARY).param("currency", "USD").with(jwt().authorities(authority(MANAGER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        // The reason this test exists. The client's adapter builds each KPI as `value: current ??
        // 0`,
        // so a missing key would render as a confident zero on a screen whose job is to say whether
        // the company is healthy. The key must be present, and its value must be null.
        .andExpect(jsonPath("$.data.fleetUtilizationPct.value").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.data.fleetUtilizationPct.available").value(false))
        .andExpect(
            jsonPath("$.data.fleetUtilizationPct.reasonCode").value("NO_AVAILABILITY_HISTORY"))
        // The positive control: a measured metric still serialises as a number, so the assertions
        // above are about what the contract does rather than about every field being empty.
        .andExpect(jsonPath("$.data.fleetSize.value").value(12))
        .andExpect(jsonPath("$.data.fleetSize.available").value(true))
        .andExpect(jsonPath("$.data.fleetSize.reasonCode").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.data.completeness.currency").value("USD"));
  }

  // ── authorisation: the gate closes and opens ─────────────────────────────────────────────────

  @Test
  @DisplayName("returns 403 when a dispatcher requests the executive summary")
  void should_return_forbidden_when_dispatcher_requests_the_executive_summary() throws Exception {
    // Management reporting is not operations reporting. A dispatcher reads loads, not margins.
    mockMvc
        .perform(
            get(SUMMARY).param("currency", "USD").with(jwt().authorities(authority(DISPATCHER))))
        .andExpect(status().isForbidden());

    verify(reportService, never()).executiveSummary(any(), any(), any());
  }

  @Test
  @DisplayName("returns 403 when a driver requests the executive summary")
  void should_return_forbidden_when_driver_requests_the_executive_summary() throws Exception {
    mockMvc
        .perform(get(SUMMARY).param("currency", "USD").with(jwt().authorities(authority(DRIVER))))
        .andExpect(status().isForbidden());

    verify(reportService, never()).executiveSummary(any(), any(), any());
  }

  @Test
  @DisplayName("returns 401 when the token is missing")
  void should_return_unauthorized_when_token_is_missing() throws Exception {
    // Without the security auto-configurations in @ImportAutoConfiguration this case passes for the
    // wrong reason: the slice would have no filter chain and answer 200 to an anonymous request.
    mockMvc.perform(get(SUMMARY).param("currency", "USD")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("returns 200 for a manager, and 403 for nobody below management")
  void should_allow_management_roles() throws Exception {
    given(reportService.executiveSummary(any(), any(), any())).willReturn(summary());

    mockMvc
        .perform(get(SUMMARY).param("currency", "USD").with(jwt().authorities(authority(MANAGER))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get(SUMMARY)
                .param("currency", "USD")
                .with(jwt().authorities(authority("ROLE_SUPERADMIN"))))
        .andExpect(status().isOk());
  }

  // ── the currency parameter ───────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("rejects a request with no currency")
  void should_reject_a_missing_currency() throws Exception {
    // No default. Amounts live per row in a column with no constraint, so a server-chosen unit
    // would
    // be baked into a total without the caller ever knowing which one it read.
    mockMvc
        .perform(get(SUMMARY).with(jwt().authorities(authority(MANAGER))))
        .andExpect(status().isBadRequest())
        // BAD_REQUEST rather than VALIDATION_FAILED, and the difference is worth keeping straight:
        // an
        // absent parameter never reaches Bean Validation, it fails binding. A parameter that is
        // present but malformed does reach it — see the next test, which asserts the other code.
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    verify(reportService, never()).executiveSummary(any(), any(), any());
  }

  @Test
  @DisplayName("rejects a lowercase currency code")
  void should_reject_a_lowercase_currency() throws Exception {
    // "usd" would pass an unvalidated binding and then match no rows at all, returning an empty
    // report that looks exactly like a business with no revenue.
    mockMvc
        .perform(get(SUMMARY).param("currency", "usd").with(jwt().authorities(authority(MANAGER))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // ── parameter binding on the endpoints with their own parameters ─────────────────────────────

  @Test
  @DisplayName("passes the window and the limit through to the service")
  void should_pass_the_window_and_the_limit_to_the_service() throws Exception {
    given(reportService.customerConcentration(any(), any(), any(), anyInt()))
        .willReturn(concentration());

    mockMvc
        .perform(
            get("/api/reports/customers/concentration")
                .param("currency", "USD")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-04-01T00:00:00Z")
                .param("limit", "25")
                .with(jwt().authorities(authority(MANAGER))))
        .andExpect(status().isOk());

    verify(reportService)
        .customerConcentration(
            eq("USD"),
            eq(OffsetDateTime.parse("2026-01-01T00:00:00Z")),
            eq(OffsetDateTime.parse("2026-04-01T00:00:00Z")),
            eq(25));
  }

  @Test
  @DisplayName("rejects a limit above the allowed maximum")
  void should_reject_a_limit_above_the_maximum() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/customers/concentration")
                .param("currency", "USD")
                .param("limit", "1000")
                .with(jwt().authorities(authority(MANAGER))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(reportService, never()).customerConcentration(any(), any(), any(), anyInt());
  }

  // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

  private static SimpleGrantedAuthority authority(String role) {
    return new SimpleGrantedAuthority(role);
  }

  private static ExecutiveSummaryResponse summary() {
    return new ExecutiveSummaryResponse(
        MetricValue.of(12),
        MetricValue.of(4),
        MetricValue.unavailable(MetricUnavailableReason.NO_AVAILABILITY_HISTORY),
        MetricValue.unavailable(MetricUnavailableReason.NO_TOTAL_MILES_SOURCE),
        MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS),
        MetricValue.unavailable(MetricUnavailableReason.NOT_IMPLEMENTED),
        MetricValue.of(new BigDecimal("83.33")),
        DataCompleteness.of(
            "USD",
            Map.of("trucks", 12L, "customers", 4L),
            List.of("fleetUtilizationPct", "loadedMilesPct", "onTimeDeliveryPct", "difotPct")));
  }

  private static CustomerConcentrationResponse concentration() {
    return new CustomerConcentrationResponse(
        "USD",
        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        OffsetDateTime.parse("2026-04-01T00:00:00Z"),
        MetricValue.of(new BigDecimal("71.00")),
        MetricValue.of(new BigDecimal("73.00")),
        MetricValue.of(new BigDecimal("75.00")),
        MetricValue.of(new BigDecimal("5070.00")),
        30,
        10,
        0L,
        List.of(),
        DataCompleteness.of("USD", Map.of("invoices", 0L), List.of()));
  }
}
