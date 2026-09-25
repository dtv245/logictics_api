package com.company.logicstic.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.company.logicstic.customer.CustomerStatus;
import com.company.logicstic.finance.invoice.InvoiceStatus;
import com.company.logicstic.finance.payment.PaymentStatus;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The service-level rules that no single query can enforce.
 *
 * <p>The repositories are mocked and no Spring context is started, so every case here is about what
 * the service <em>decides</em>: which statuses it passes down, which of two disagreeing answers to
 * a question it reports rather than resolving, and — the case this class exists for — that no
 * response it builds ever carries a number for something it could not measure.
 *
 * <p>The world each test starts in is an empty one. Mockito's default answer returns an empty
 * collection for a collection-returning method, so an unstubbed repository means "no rows", which
 * is both the honest default for a test about missing data and the state this deployment is
 * actually in: nothing writes expenses or odometer readings yet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportServiceImpl")
class ReportServiceImplTest {

  private static final String USD = "USD";

  private static final Instant NOW = Instant.parse("2026-06-15T09:30:00Z");

  private static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-06-15T09:30:00Z");

  /** The real resolver on a fixed clock: a mocked one would assert nothing about the windows. */
  private static final ReportPeriodResolver PERIOD_RESOLVER =
      new ReportPeriodResolver(Clock.fixed(NOW, ZoneOffset.UTC));

  @Mock private ReportingInvoiceRepository invoices;
  @Mock private ReportingPaymentRepository payments;
  @Mock private ReportingLoadRepository loads;
  @Mock private ReportingTruckRepository trucks;
  @Mock private ReportingCustomerRepository customers;
  @Mock private ReportingExpenseRepository expenses;
  @Mock private ReportingMaintenanceRecordRepository maintenanceRecords;
  @Mock private ReportingMaintenanceScheduleRepository maintenanceSchedules;
  @Mock private ReportingMileageRepository mileage;

  @Captor private ArgumentCaptor<Collection<String>> statusCaptor;

  // ── rule 3: statuses are matched case-insensitively, so they must be bound lowercase ─────────

  @Test
  @DisplayName("passes only lowercase revenue-bearing statuses to the repository")
  void should_pass_only_lowercase_revenue_bearing_statuses_to_the_repository() {
    // when
    service().monthlyFinancials(USD, null, null);

    // then
    verify(invoices).findMonthlyRevenue(eq(USD), statusCaptor.capture(), any(), any());
    Collection<String> passed = statusCaptor.getValue();

    assertThat(passed).isNotEmpty();
    // The executable form of the repository's rule. invoices.status holds both "Issued" and
    // "issued", and the query compares LOWER(i.status) against these values — so a single
    // capitalised literal here would not raise an error, it would silently drop real revenue.
    assertThat(passed)
        .allSatisfy(status -> assertThat(status).isEqualTo(status.toLowerCase(Locale.ROOT)))
        .containsExactlyInAnyOrderElementsOf(InvoiceStatus.revenueBearingLowercase())
        .doesNotContain("Issued", "Draft", "draft", "Cancelled", "Rejected", "PendingApproval");
  }

  @Test
  @DisplayName("passes both spellings of a settled payment to the repository")
  void should_pass_both_settled_payment_spellings_to_the_repository() {
    // when
    service().receivablesAging(USD, null, null, null);

    // then
    verify(payments).sumSettledByInvoice(eq(USD), statusCaptor.capture());
    // "completed" comes from the seeder and "paid" from the integration tests; both are asserted
    // against rows that exist, so binding only one would under-report cash and overstate debt.
    assertThat(statusCaptor.getValue())
        .containsExactlyInAnyOrderElementsOf(PaymentStatus.settledLowercase())
        .contains("completed", "paid")
        .doesNotContain("pending", "failed", "refunded");
  }

  @Test
  @DisplayName("counts active customers from the status enum's own value")
  void should_count_active_customers_from_the_status_enum() {
    // given
    givenNoDeliveriesInWindow();

    // when
    service().executiveSummary(USD, null, null);

    // then
    verify(customers).countByStatusIn(statusCaptor.capture());
    assertThat(statusCaptor.getValue())
        .containsExactlyInAnyOrderElementsOf(CustomerStatus.activeLowercase())
        .doesNotContain("Active", "ACTIVE");
  }

  // ── rule 1: an unavailable metric is never a number ─────────────────────────────────────────

  @Test
  @DisplayName("never reports an unavailable metric as a number, in any of the six responses")
  void should_never_return_a_primitive_zero_for_an_unavailable_metric() {
    // given an entirely empty world — the state the executive screen is in today, and the state in
    // which a fabricated zero is most convincing
    givenNoDeliveriesInWindow();

    // when
    List<Object> responses =
        List.of(
            service().executiveSummary(USD, null, null),
            service().monthlyFinancials(USD, null, null),
            service().costsByCategory(USD, null, null),
            service().fleetHealth(USD, null, null),
            service().customerConcentration(USD, null, null, 10),
            service().receivablesAging(USD, null, null, null));

    // then
    List<MetricValue> metrics = new ArrayList<>();
    responses.forEach(response -> collectMetrics(response, metrics));

    // Guards the guard: a walker that silently found nothing would let every assertion below pass.
    assertThat(metrics).hasSizeGreaterThan(50);
    assertThat(metrics)
        .allSatisfy(
            metric -> {
              // Both directions, because both are bugs the client would show as a measurement: an
              // unavailable metric carrying a number, and an available one carrying null (which the
              // client's `value: current ?? 0` would render as a confident zero).
              assertThat(metric.available()).isEqualTo(metric.value() != null);
              if (!metric.available()) {
                assertThat(metric.value()).isNull();
                assertThat(metric.reasonCode()).isNotBlank();
              }
            });
  }

  @Test
  @DisplayName("keeps operating profit unavailable when cost is unavailable")
  void should_keep_operating_profit_unavailable_when_cost_is_unavailable() {
    // given a month with revenue and no expense rows at all
    given(invoices.findMonthlyRevenue(eq(USD), any(), any(), any()))
        .willReturn(List.of(new InvoiceMonthlyRevenue(2026, 3, new BigDecimal("1000.00"), 2)));

    // when
    MonthlyFinancialsResponse.Point march =
        service().monthlyFinancials(USD, null, null).points().get(0);

    // then
    assertThat(march.revenue().value()).isEqualByComparingTo("1000.00");
    assertThat(march.operatingCost().available()).isFalse();
    assertThat(march.operatingProfit().available()).isFalse();
    assertThat(march.operatingProfit().value()).isNull();
    // The flattering failure: cost unknown, so nothing is subtracted, and profit comes out equal to
    // revenue. On a screen whose job is to say whether the company is healthy, that is the worst
    // number this endpoint could produce.
    assertThat(march.operatingProfit()).isNotEqualTo(march.revenue());
    assertThat(march.contributionSpread().available()).isFalse();
  }

  @Test
  @DisplayName("reports total miles as unavailable even when loaded miles exist")
  void should_not_report_loaded_distance_as_total_miles() {
    // given a month of delivered loads with distance on them
    given(loads.findMonthlyMiles(any(), any(), any()))
        .willReturn(List.of(new LoadMonthlyMiles(2026, 3, 5000.0, 3)));

    // when
    MonthlyFinancialsResponse.Point march =
        service().monthlyFinancials(USD, null, null).points().get(0);

    // then
    assertThat(march.month()).isEqualTo("2026-03");
    assertThat(march.loadedMiles().value()).isEqualByComparingTo("5000.0");
    // loads.distance excludes every empty and deadhead mile. Publishing it under totalMiles would
    // relabel a loaded-distance figure as a total-distance one, and every rate built on it would
    // inherit the difference.
    assertThat(march.totalMiles().available()).isFalse();
    assertThat(march.totalMiles().reasonCode()).isEqualTo("NO_TOTAL_MILES_SOURCE");
    assertThat(march.totalMiles()).isNotEqualTo(march.loadedMiles());
  }

  @Test
  @DisplayName("reports fleet activity under its own key and leaves utilisation unavailable")
  void should_report_trucks_with_loads_over_fleet_size() {
    // given three of twenty trucks carried something
    given(trucks.count()).willReturn(20L);
    given(loads.findTruckIdsWithActivity(any(), any(), any()))
        .willReturn(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    givenNoDeliveriesInWindow();

    // when
    ExecutiveSummaryResponse response = service().executiveSummary(USD, null, null);

    // then
    assertThat(response.trucksWithLoadsPct().value()).isEqualByComparingTo("15.00");
    assertThat(response.fleetUtilizationPct().available()).isTrue();
    assertThat(response.fleetUtilizationPct().value()).isEqualByComparingTo("0.00");
    assertThat(response.difotPct().available()).isFalse();
    assertThat(response.loadedMilesPct().available()).isFalse();
    // Nothing delivered in the window means nothing was on time and nothing was late — a zero here
    // would read as a fleet that has never missed a delivery date.
    assertThat(response.onTimeDeliveryPct().available()).isFalse();
    assertThat(response.onTimeDeliveryPct().value()).isNull();
  }

  @Test
  @DisplayName("computes DIFOT percentage when comparable delivered loads exist")
  void should_compute_difot_percentage() {
    // given 100 comparable loads, 85 delivered in full and on time
    given(trucks.count()).willReturn(10L);
    given(loads.countOnTime(any(), any(), any())).willReturn(new LoadOnTimeCounts(100L, 90L));
    given(loads.countDifot(any(), any(), any())).willReturn(new LoadDifotCounts(100L, 85L));

    // when
    ExecutiveSummaryResponse response = service().executiveSummary(USD, null, null);

    // then
    assertThat(response.onTimeDeliveryPct().value()).isEqualByComparingTo("90.00");
    assertThat(response.difotPct().available()).isTrue();
    assertThat(response.difotPct().value()).isEqualByComparingTo("85.00");
  }

  @Test
  @DisplayName("computes unplanned downtime and breakdown rates for fleet health")
  void should_compute_unplanned_downtime_and_breakdown_rates() {
    // given 10 trucks, 1 breakdown, 50,000 miles driven
    given(trucks.count()).willReturn(10L);
    given(maintenanceRecords.countWithDowntimeInterval()).willReturn(2L);
    given(maintenanceRecords.findUnplannedDowntimes(any(), any()))
        .willReturn(
            List.of(
                new DowntimeInterval(
                    UUID.randomUUID(),
                    OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                    OffsetDateTime.parse("2026-06-02T00:00:00Z"))));
    given(maintenanceRecords.countBreakdowns(any(), any())).willReturn(1L);
    given(maintenanceSchedules.countActiveWithDueDate()).willReturn(20L);
    given(maintenanceSchedules.countActiveWithDueDateNotPast(any())).willReturn(19L);
    given(mileage.findReadingSpreadByTruck(any(), any()))
        .willReturn(List.of(new TruckDistance(UUID.randomUUID(), 10000, 60000, 2)));

    // when
    FleetHealthResponse response = service().fleetHealth(USD, null, null);

    // then
    assertThat(response.unplannedDowntimePct().available()).isTrue();
    assertThat(response.pmCompliancePct().value()).isEqualByComparingTo("95.00");
    assertThat(response.breakdownsPer100kMiles().available()).isTrue();
    assertThat(response.breakdownsPer100kMiles().value()).isEqualByComparingTo("2.00");
  }

  // ── cost attribution ────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("counts an expense once when both vehicle foreign keys are set")
  void should_not_double_count_expense_when_both_truck_foreign_keys_are_set() {
    // given three rows: one whose two vehicle links disagree, one naming the same truck twice, and
    // one naming a single truck
    UUID truck = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    given(expenses.findLines(anyString(), any(), any()))
        .willReturn(
            List.of(
                expense("100.00", truck, other),
                expense("50.00", truck, truck),
                expense("25.00", truck, null)));

    // when
    CostBreakdownResponse response = service().costsByCategory(USD, null, null);

    // then
    // 175, not 275: expenses carries two vehicle columns, and a query joining or grouping on either
    // — or on COALESCE of the two — can visit the same row twice.
    assertThat(response.totalCost().value()).isEqualByComparingTo("175.00");
    assertThat(category(response, CostCategory.UNCLASSIFIED).currentTotal().value())
        .isEqualByComparingTo("175.00");
    // Reported rather than resolved: the money is still counted, and the number of rows whose
    // attribution is in doubt travels beside it.
    assertThat(response.ambiguousTruckLinkCount()).isEqualTo(1);
  }

  // ── receivables ─────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("buckets an invoice with no due date into its own band, never into current")
  void should_bucket_invoice_into_no_due_date_when_due_date_is_null() {
    // given a 500.00 invoice nobody has established a due date for
    given(invoices.findRevenueBearingBalances(eq(USD), any()))
        .willReturn(List.of(balance(new BigDecimal("500.00"), null, "Issued")));

    // when
    ReceivablesAgingResponse response = service().receivablesAging(USD, AS_OF, null, null);

    // then
    assertThat(bucket(response, AgingBucket.NO_DUE_DATE).amount().value())
        .isEqualByComparingTo("500.00");
    assertThat(bucket(response, AgingBucket.NO_DUE_DATE).invoiceCount()).isEqualTo(1);
    // Current is the healthiest-looking band on the report. Sweeping unknown rows into it would let
    // genuinely stale debt hide there, which is the most consequential way an ageing report can
    // lie.
    assertThat(bucket(response, AgingBucket.CURRENT).amount().value()).isEqualByComparingTo("0.00");
    assertThat(bucket(response, AgingBucket.CURRENT).invoiceCount()).isZero();
    assertThat(response.outstandingTotal().value()).isEqualByComparingTo("500.00");
    // Not overdue either: nothing has shown it is past due, only that nobody said when it falls
    // due.
    assertThat(response.overdueTotal().value()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("reports DSO as unavailable, not zero, when the window sold nothing")
  void should_report_dso_as_unavailable_when_the_window_sold_nothing() {
    // given an outstanding invoice and no revenue in the window
    given(invoices.findRevenueBearingBalances(eq(USD), any()))
        .willReturn(List.of(balance(new BigDecimal("500.00"), AS_OF.plusDays(10), "Issued")));
    given(invoices.sumRevenue(eq(USD), any(), any(), any())).willReturn(null);

    // when
    ReceivablesAgingResponse response = service().receivablesAging(USD, AS_OF, null, null);

    // then
    assertThat(response.outstandingTotal().value()).isEqualByComparingTo("500.00");
    assertThat(response.dsoDays().available()).isFalse();
    assertThat(response.dsoDays().reasonCode()).isEqualTo("ZERO_DENOMINATOR");
    // Zero would read as "collected instantly", which is the best possible number on this endpoint
    // and the exact opposite of what a window with no sales means.
    assertThat(response.dsoDays().value()).isNull();
  }

  @Test
  @DisplayName("counts an invoice whose status and payments disagree about settlement")
  void should_count_an_invoice_whose_status_and_payments_disagree() {
    // given an invoice whose status says Paid and which has no payment rows behind it
    given(invoices.findRevenueBearingBalances(eq(USD), any()))
        .willReturn(List.of(balance(new BigDecimal("500.00"), AS_OF, "Paid")));

    // when
    ReceivablesAgingResponse response = service().receivablesAging(USD, AS_OF, null, null);

    // then
    // The disagreement is a finding, not an error to be resolved. Preferring whichever source is
    // convenient would hide exactly the rows someone should go and look at.
    assertThat(response.invoicesWithStatusPaymentMismatch()).isEqualTo(1);
    // Aged from money actually received, so the invoice is still outstanding despite saying Paid.
    assertThat(response.outstandingTotal().value()).isEqualByComparingTo("500.00");
  }

  // ── concentration ───────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("limits the rows without limiting the index")
  void should_limit_rows_without_limiting_the_index() {
    // given thirty customers, seventy-one percent of the revenue on one of them
    given(invoices.findRevenueByCustomer(eq(USD), any(), any(), any()))
        .willReturn(thirtyCustomers());

    // when
    CustomerConcentrationResponse response = service().customerConcentration(USD, null, null, 10);

    // then
    assertThat(response.customersConsidered()).isEqualTo(30);
    assertThat(response.customersReturned()).isEqualTo(10);
    assertThat(response.rows()).hasSize(10);
    // 71² + 29 × 1² = 5070, computed over all thirty. Recomputed from the ten returned rows it
    // would
    // come out higher — the truncation changes the denominator underneath it — so the client is
    // handed the index rather than asked to derive it.
    assertThat(response.hhi().value()).isEqualByComparingTo("5070.00");
    assertThat(response.rows().get(0).shareOfRevenue().value()).isEqualByComparingTo("71.00");
    assertThat(response.rows().get(0).grossMarginPercent().available()).isFalse();
    assertThat(response.rows().get(0).grossMarginPercent().reasonCode())
        .isEqualTo("NO_COST_ALLOCATION");
  }

  @Test
  @DisplayName("computes HHI and top shares over more than 100 customers with complete population")
  void should_compute_hhi_and_top_shares_over_more_than_100_customers() {
    List<InvoiceCustomerRevenue> revenues = new ArrayList<>();
    revenues.add(
        new InvoiceCustomerRevenue(UUID.randomUUID(), "Dominant", new BigDecimal("50.00"), 1));
    for (int i = 0; i < 100; i++) {
      revenues.add(
          new InvoiceCustomerRevenue(
              UUID.randomUUID(), "Customer " + i, new BigDecimal("0.50"), 1));
    }
    given(invoices.findRevenueByCustomer(eq(USD), any(), any(), any())).willReturn(revenues);

    CustomerConcentrationResponse response = service().customerConcentration(USD, null, null, 10);

    assertThat(response.customersConsidered()).isEqualTo(101);
    assertThat(response.customersReturned()).isEqualTo(10);
    assertThat(response.rows()).hasSize(10);
    assertThat(response.top1Share().value()).isEqualByComparingTo("50.00");
    // 50^2 + 100 * 0.5^2 = 2500 + 25 = 2525.00
    assertThat(response.hhi().value()).isEqualByComparingTo("2525.00");
  }

  // ── helpers ─────────────────────────────────────────────────────────────────────────────────

  private ReportServiceImpl service() {
    return new ReportServiceImpl(
        invoices,
        payments,
        loads,
        trucks,
        customers,
        expenses,
        maintenanceRecords,
        maintenanceSchedules,
        mileage,
        PERIOD_RESOLVER);
  }

  /**
   * Walks a response and collects every {@link MetricValue} inside it.
   *
   * <p>Reflection rather than a field-by-field list, so a metric added to a response later is
   * covered by the rule without anyone remembering to add it here. The walk descends records,
   * collections and map values, and stops at anything else — a {@code BigDecimal} below a metric, a
   * timestamp, a label.
   */
  private static void collectMetrics(Object value, List<MetricValue> found) {
    if (value == null) {
      return;
    }
    if (value instanceof MetricValue metric) {
      found.add(metric);
      return;
    }
    if (value instanceof Collection<?> collection) {
      collection.forEach(item -> collectMetrics(item, found));
      return;
    }
    if (value instanceof Map<?, ?> map) {
      map.values().forEach(item -> collectMetrics(item, found));
      return;
    }
    if (!value.getClass().isRecord()) {
      return;
    }
    for (RecordComponent component : value.getClass().getRecordComponents()) {
      try {
        collectMetrics(component.getAccessor().invoke(value), found);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Could not read " + component.getName(), e);
      }
    }
  }

  private static ExpenseLine expense(String amount, UUID truckId, UUID otherTruckId) {
    return new ExpenseLine(
        UUID.randomUUID(),
        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
        "Miscellaneous Vendor Charge",
        null,
        null,
        new BigDecimal(amount),
        truckId,
        otherTruckId,
        "approved");
  }

  private static InvoiceBalance balance(BigDecimal amount, OffsetDateTime dueDate, String status) {
    return new InvoiceBalance(UUID.randomUUID(), UUID.randomUUID(), amount, dueDate, status);
  }

  private static List<InvoiceCustomerRevenue> thirtyCustomers() {
    List<InvoiceCustomerRevenue> rows = new ArrayList<>();
    rows.add(new InvoiceCustomerRevenue(UUID.randomUUID(), "Dominant", new BigDecimal("71.00"), 1));
    for (int index = 0; index < 29; index++) {
      rows.add(
          new InvoiceCustomerRevenue(
              UUID.randomUUID(), "Small " + index, new BigDecimal("1.00"), 1));
    }
    return rows;
  }

  /**
   * The on-time aggregate is a projection rather than a collection, so it does not get Mockito's
   * empty-collection default — it gets {@code null}, which no database would ever return. A {@code
   * COUNT} with no {@code GROUP BY} always produces exactly one row, so the empty world is a row of
   * zeros, and stubbing it that way keeps the fixture as honest as the production shape.
   */
  private void givenNoDeliveriesInWindow() {
    given(loads.countOnTime(any(), any(), any())).willReturn(new LoadOnTimeCounts(0L, 0L));
    given(loads.countDifot(any(), any(), any())).willReturn(new LoadDifotCounts(0L, 0L));
  }

  private static ReceivablesAgingResponse.Bucket bucket(
      ReceivablesAgingResponse response, AgingBucket agingBucket) {
    return response.buckets().stream()
        .filter(candidate -> candidate.id().equals(agingBucket.id()))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("No bucket " + agingBucket.id() + " in the response"));
  }

  private static CostBreakdownResponse.Category category(
      CostBreakdownResponse response, CostCategory costCategory) {
    return response.categories().stream()
        .filter(candidate -> candidate.id().equals(costCategory.id()))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("No category " + costCategory.id() + " in the response"));
  }
}
