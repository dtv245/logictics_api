package com.company.logicstic.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.finance.invoice.InvoiceStatus;
import com.company.logicstic.finance.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Executes the reporting aggregates against a real PostgreSQL, with Flyway having applied V1–V4.
 *
 * <p>Booting the context already proves every {@code @Query} parses, because Spring Data validates
 * them when the repository beans are created. What this class adds is the harder half: that the
 * queries return the <em>right</em> numbers. A JPQL statement can be perfectly valid and still sum
 * amounts across currencies, count rows twice through two foreign keys, or miss every invoice whose
 * status was written in the other casing.
 *
 * <p>Rows are inserted with raw SQL rather than through the services. The services normalise status
 * on the way in, and the whole point of the status tests is to place the exact spellings this
 * database actually contains — {@code Issued} from the seeder beside {@code issued} from the
 * dispatch listener — which a service would have flattened to one form.
 *
 * <p>The class is {@code @Transactional}, so each test rolls back and the shared container stays
 * clean between them. That is also why the assertions read their own inserts: within one
 * transaction the repository sees exactly the rows the test wrote, and nothing else.
 */
@EnabledIf("dockerIsAvailable")
@SpringBootTest
@Transactional
@DisplayName("Reporting aggregates against PostgreSQL")
class ReportingRepositoryIT {

  private static final GenericContainer<?> POSTGRES =
      new GenericContainer<>(DockerImageName.parse("postgres:18-alpine"))
          .withExposedPorts(5432)
          .withEnv("POSTGRES_DB", "logisticsx_it")
          .withEnv("POSTGRES_USER", "postgres")
          .withEnv("POSTGRES_PASSWORD", "postgres")
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

  private static final String USD = "USD";
  private static final String VND = "VND";

  private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private static final OffsetDateTime TO = OffsetDateTime.parse("2026-04-01T00:00:00Z");

  private static final List<String> REVENUE_STATUSES =
      List.copyOf(InvoiceStatus.revenueBearingLowercase());
  private static final List<String> SETTLED_STATUSES =
      List.copyOf(PaymentStatus.settledLowercase());

  private final ReportingInvoiceRepository invoices;
  private final ReportingPaymentRepository payments;
  private final ReportingMileageRepository mileage;
  private final JdbcTemplate jdbc;

  @Autowired
  ReportingRepositoryIT(
      ReportingInvoiceRepository invoices,
      ReportingPaymentRepository payments,
      ReportingMileageRepository mileage,
      JdbcTemplate jdbc) {
    this.invoices = invoices;
    this.payments = payments;
    this.mileage = mileage;
    this.jdbc = jdbc;
  }

  static boolean dockerIsAvailable() {
    return DockerClientFactory.instance().isDockerAvailable();
  }

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/logisticsx_it"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    // Redis and every cache bean are @ConditionalOnProperty(app.cache.enabled=true). Reporting
    // reads
    // no cache, so leaving them off keeps this class free of infrastructure it never touches.
    registry.add("app.cache.enabled", () -> "false");
    registry.add("app.security.jwt.issuer", () -> "https://issuer.test");
    registry.add("app.security.jwt.audience", () -> "logisticsx.api");
    registry.add("app.security.jwt.jwk-set-uri", () -> "https://issuer.test/jwks");
  }

  @BeforeAll
  static void startPostgres() {
    POSTGRES.start();
  }

  @AfterAll
  static void stopPostgres() {
    POSTGRES.stop();
  }

  // ── the casing rule ─────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("sums revenue per month across both spellings of a status")
  void should_sum_revenue_per_month_ignoring_status_casing() {
    insertInvoice("Issued", USD, "100.00", "2026-01-15T10:00:00Z");
    insertInvoice("issued", USD, "200.00", "2026-01-20T10:00:00Z");
    insertInvoice("Issued", USD, "300.00", "2026-02-10T10:00:00Z");
    // Not revenue: a draft has not been billed, so it is excluded however it is spelled.
    insertInvoice("Draft", USD, "999.00", "2026-01-25T10:00:00Z");
    insertInvoice("draft", USD, "999.00", "2026-01-26T10:00:00Z");

    List<InvoiceMonthlyRevenue> revenue =
        invoices.findMonthlyRevenue(USD, REVENUE_STATUSES, FROM, TO);

    assertThat(revenue)
        .containsExactly(
            new InvoiceMonthlyRevenue(2026, 1, new BigDecimal("300.00"), 2),
            new InvoiceMonthlyRevenue(2026, 2, new BigDecimal("300.00"), 1));
  }

  @Test
  @DisplayName("excludes other-currency rows from the total and counts them separately")
  void should_exclude_other_currency_rows_and_count_them() {
    insertInvoice("Issued", USD, "100.00", "2026-01-15T10:00:00Z");
    insertInvoice("Issued", VND, "5000000.00", "2026-01-16T10:00:00Z");
    insertInvoice("Issued", VND, "7000000.00", "2026-02-16T10:00:00Z");

    assertThat(invoices.sumRevenue(USD, REVENUE_STATUSES, FROM, TO)).isEqualByComparingTo("100.00");
    // Two VND rows sat inside the window and were dropped by the currency predicate, not by a
    // filter applied afterwards — so this is the number of rows the total is missing.
    assertThat(invoices.countInOtherCurrency(USD, FROM, TO)).isEqualTo(2);
    assertThat(invoices.findDistinctCurrencies(FROM, TO)).containsExactly(USD, VND);
  }

  @Test
  @DisplayName("ignores invoices outside the window in both directions")
  void should_honour_the_half_open_window() {
    insertInvoice("Issued", USD, "10.00", "2025-12-31T23:59:59Z");
    insertInvoice("Issued", USD, "20.00", "2026-01-01T00:00:00Z");
    insertInvoice("Issued", USD, "40.00", "2026-03-31T23:59:59Z");
    insertInvoice("Issued", USD, "80.00", "2026-04-01T00:00:00Z");

    List<InvoiceMonthlyRevenue> revenue =
        invoices.findMonthlyRevenue(USD, REVENUE_STATUSES, FROM, TO);

    // The two boundary rows are exactly the ones that decide whether the window is half-open. 20
    // is in, 80 is out, and 10 is out: a sum of 150 would mean the boundary was mishandled.
    assertThat(revenue)
        .extracting(InvoiceMonthlyRevenue::revenue)
        .containsExactly(new BigDecimal("20.00"), new BigDecimal("40.00"));
  }

  @Test
  @DisplayName("aggregates accurately over more than 100 source records without truncation")
  void should_aggregate_over_more_than_100_source_invoices() {
    for (int i = 0; i < 120; i++) {
      insertInvoice("Issued", USD, "10.00", "2026-01-15T10:00:00Z");
    }

    BigDecimal total = invoices.sumRevenue(USD, REVENUE_STATUSES, FROM, TO);
    assertThat(total).isEqualByComparingTo("1200.00");

    List<InvoiceMonthlyRevenue> monthly =
        invoices.findMonthlyRevenue(USD, REVENUE_STATUSES, FROM, TO);
    assertThat(monthly).hasSize(1);
    assertThat(monthly.get(0).invoiceCount()).isEqualTo(120);
    assertThat(monthly.get(0).revenue()).isEqualByComparingTo("1200.00");
  }

  // ── receivables ─────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("sums settled payments per invoice across both settled spellings")
  void should_sum_settled_payments_by_invoice() {
    UUID invoiceId = insertInvoice("Issued", USD, "100.00", "2026-01-15T10:00:00Z");
    UUID otherInvoiceId = insertInvoice("Issued", USD, "250.00", "2026-01-16T10:00:00Z");

    insertPayment(invoiceId, "completed", USD, "60.00", "2026-02-01T10:00:00Z");
    insertPayment(invoiceId, "paid", USD, "40.00", "2026-02-02T10:00:00Z");
    insertPayment(invoiceId, "failed", USD, "999.00", "2026-02-03T10:00:00Z");
    insertPayment(otherInvoiceId, "PAID", USD, "25.00", "2026-02-04T10:00:00Z");

    List<InvoicePayments> settled = payments.sumSettledByInvoice(USD, SETTLED_STATUSES);

    // 100 for the first invoice — the failed payment is money that never arrived — and 25 for the
    // second, whose status was written in a third casing again.
    assertThat(settled)
        .containsExactlyInAnyOrder(
            new InvoicePayments(invoiceId, new BigDecimal("100.00")),
            new InvoicePayments(otherInvoiceId, new BigDecimal("25.00")));
  }

  @Test
  @DisplayName("reports every revenue-bearing invoice regardless of how old it is")
  void should_return_open_invoices_with_no_date_filter() {
    insertInvoice("Issued", USD, "100.00", "2024-01-15T10:00:00Z");
    insertInvoice("issued", USD, "200.00", "2026-02-15T10:00:00Z");
    insertInvoice("Draft", USD, "999.00", "2026-02-16T10:00:00Z");

    List<InvoiceBalance> balances = invoices.findRevenueBearingBalances(USD, REVENUE_STATUSES);

    // The 2024 invoice is the point: receivables is a position, not a flow. A two-year-old unpaid
    // invoice is still outstanding today, and a windowed query would have hidden exactly the debt
    // the report exists to find.
    assertThat(balances)
        .extracting(InvoiceBalance::totalAmount)
        .containsExactlyInAnyOrder(new BigDecimal("100.00"), new BigDecimal("200.00"));
  }

  @Test
  @DisplayName("keeps the invoice status word verbatim so a mismatch can be detected later")
  void should_return_the_stored_status_word_unnormalised() {
    insertInvoice("partially_paid", USD, "100.00", "2026-01-15T10:00:00Z");
    insertInvoice("PartiallyPaid", USD, "100.00", "2026-01-16T10:00:00Z");

    // Both spellings are revenue-bearing and both must come back exactly as stored: the service
    // compares the status against the payments to find rows where the two disagree, and normalising
    // here would decide that question before it was asked.
    assertThat(invoices.findRevenueBearingBalances(USD, REVENUE_STATUSES))
        .extracting(InvoiceBalance::status)
        .containsExactlyInAnyOrder("partially_paid", "PartiallyPaid");
  }

  // ── odometer readings ───────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns a single-reading truck rather than hiding it behind a zero spread")
  void should_report_a_truck_with_only_one_reading() {
    UUID measured = insertTruck("T-1");
    UUID unmeasured = insertTruck("T-2");

    insertReading(measured, 1000, "2026-01-10T10:00:00Z");
    insertReading(measured, 1500, "2026-02-10T10:00:00Z");
    insertReading(measured, 1900, "2026-03-10T10:00:00Z");
    insertReading(unmeasured, 800, "2026-01-10T10:00:00Z");

    List<TruckDistance> spread = mileage.findReadingSpreadByTruck(FROM, TO);

    // The second truck has a highest-minus-lowest of exactly zero. That zero is not "did not move",
    // it is "one data point", so the query must surface the row for the service to exclude and
    // count — a query that filtered it out would leave the fleet total looking complete.
    assertThat(spread)
        .containsExactlyInAnyOrder(
            new TruckDistance(measured, 1000, 1900, 3), new TruckDistance(unmeasured, 800, 800, 1));
  }

  // ── helpers ─────────────────────────────────────────────────────────────────────────────────

  private UUID insertInvoice(String status, String currency, String total, String createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO invoices (id, type, status, subtotal_amount, subtotal_currency,
                              tax_total_amount, tax_total_currency, total_amount, total_currency,
                              created_at)
        VALUES (?, 'standard', ?, ?::numeric, ?, 0.00, ?, ?::numeric, ?, ?::timestamptz)
        """,
        id,
        status,
        total,
        currency,
        currency,
        total,
        currency,
        createdAt);
    return id;
  }

  private void insertPayment(
      UUID invoiceId, String status, String currency, String amount, String createdAt) {
    jdbc.update(
        """
        INSERT INTO payments (id, status, amount_amount, amount_currency, invoice_id,
                              billing_address_city, billing_address_country, billing_address_line1,
                              billing_address_state, billing_address_zip_code, created_at)
        VALUES (?, ?, ?::numeric, ?, ?, 'Hanoi', 'VN', '1 Test St', 'HN', '10000', ?::timestamptz)
        """,
        UUID.randomUUID(),
        status,
        amount,
        currency,
        invoiceId,
        createdAt);
  }

  private UUID insertTruck(String number) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO trucks (id, "number", "type", vehicle_capacity, status, is_hazmat_placarded,
                            adr_equipment_allowed_classes, adr_equipment_is_adr_certified)
        VALUES (?, ?, 'dry_van', 20, 'Active', false, 'none', false)
        """,
        id,
        number);
    return id;
  }

  private void insertReading(UUID truckId, int value, String recordedAt) {
    jdbc.update(
        """
        INSERT INTO vehicle_mileage_readings (id, truck_id, reading_value, recorded_at, source)
        VALUES (?, ?, ?, ?::timestamptz, 'test')
        """,
        UUID.randomUUID(),
        truckId,
        value,
        recordedAt);
  }
}
