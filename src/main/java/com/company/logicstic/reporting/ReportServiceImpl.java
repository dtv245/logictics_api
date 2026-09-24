package com.company.logicstic.reporting;

import com.company.logicstic.customer.CustomerStatus;
import com.company.logicstic.finance.invoice.InvoiceStatus;
import com.company.logicstic.finance.payment.PaymentStatus;
import com.company.logicstic.load.core.LoadStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the six reporting aggregates.
 *
 * <p>This class owns the decisions that turn rows into numbers, and it makes them in one place so
 * they can be read together: which statuses count as revenue, what a zero denominator means, and
 * which of the client's requested metrics this schema cannot support at all. The arithmetic itself
 * lives in {@link ReportingMetricsCalculator}, which has no dependencies and is tested on its own.
 *
 * <p>It does not extend {@code AbstractBaseService}. That template is five CRUD operations over one
 * repository; this service has nine repositories and writes nothing, so inheriting it would supply
 * methods that cannot be implemented and a transaction boundary that means something different.
 *
 * <p>Read-only, and declared so. Every query here is an aggregate, and a transaction that cannot
 * write is one fewer way for a reporting request to change the data it is reporting on.
 */
@Profile("!nodb")
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

  /**
   * Statuses that count as billed revenue, lowercased for {@code LOWER(i.status) IN :statuses}.
   *
   * <p>Built from the enum rather than written as literals, so the decision lives with the status
   * vocabulary and a capitalised spelling cannot be introduced here by hand.
   */
  private static final List<String> REVENUE_STATUSES =
      List.copyOf(InvoiceStatus.revenueBearingLowercase());

  /** Statuses that mean the money arrived. Both observed spellings, from the enum. */
  private static final List<String> SETTLED_STATUSES =
      List.copyOf(PaymentStatus.settledLowercase());

  /**
   * A load that was actually delivered.
   *
   * <p>Only this status, because on-time delivery and loaded miles are both claims about work
   * completed. A dispatched load has a promised date but no delivery to measure against it.
   */
  private static final List<String> DELIVERED_LOAD_STATUSES =
      List.of(LoadStatus.DELIVERED.dbValue());

  /**
   * A load that put a truck on the road: dispatched, picked up, or delivered.
   *
   * <p>Wider than {@link #DELIVERED_LOAD_STATUSES} on purpose. The question this answers is "did
   * this vehicle move anything", and a load cancelled in draft never moved a truck, while one still
   * in transit has.
   */
  private static final List<String> ACTIVE_LOAD_STATUSES =
      List.of(
          LoadStatus.DISPATCHED.dbValue(),
          LoadStatus.PICKED_UP.dbValue(),
          LoadStatus.DELIVERED.dbValue());

  /**
   * Days sales outstanding is a count of days; one decimal is already more than the data supports.
   */
  private static final int DSO_SCALE = 1;

  private final ReportingInvoiceRepository invoices;
  private final ReportingPaymentRepository payments;
  private final ReportingLoadRepository loads;
  private final ReportingTruckRepository trucks;
  private final ReportingCustomerRepository customers;
  private final ReportingExpenseRepository expenses;
  private final ReportingMaintenanceRecordRepository maintenanceRecords;
  private final ReportingMaintenanceScheduleRepository maintenanceSchedules;
  private final ReportingMileageRepository mileage;
  private final ReportPeriodResolver periodResolver;

  // ── Executive summary ────────────────────────────────────────────────────────────────────────

  @Override
  public ExecutiveSummaryResponse executiveSummary(
      String currency, OffsetDateTime from, OffsetDateTime to) {
    ReportPeriodResolver.Period period = periodResolver.resolve(from, to);

    long fleetSize = trucks.count();
    long activeCustomers = customers.countByStatusIn(CustomerStatus.activeLowercase());

    List<UUID> trucksWithLoads =
        loads.findTruckIdsWithActivity(ACTIVE_LOAD_STATUSES, period.from(), period.to());
    MetricValue trucksWithLoadsPct =
        ReportingMetricsCalculator.percentage(
            trucksWithLoads.size(), fleetSize, MetricUnavailableReason.NO_SOURCE_ROWS);

    LoadOnTimeCounts onTime =
        loads.countOnTime(DELIVERED_LOAD_STATUSES, period.from(), period.to());
    MetricValue onTimeDeliveryPct =
        onTime == null
            ? MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS)
            : ReportingMetricsCalculator.percentage(
                onTime.onTimeCount(),
                onTime.comparableCount(),
                MetricUnavailableReason.NO_SOURCE_ROWS);

    // Fleet utilization: active vehicle duration vs scheduled fleet duration in window
    long windowSeconds = Duration.between(period.from(), period.to()).toSeconds();
    long totalScheduledSeconds = fleetSize * windowSeconds;
    MetricValue fleetUtilizationPct;
    if (fleetSize <= 0 || windowSeconds <= 0) {
      fleetUtilizationPct = MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    } else {
      List<LoadActiveDuration> activeDurations =
          loads.findActiveLoadDurations(ACTIVE_LOAD_STATUSES, period.from(), period.to());
      Map<UUID, List<Interval>> intervalsByTruck = new HashMap<>();
      if (activeDurations != null) {
        for (LoadActiveDuration d : activeDurations) {
          OffsetDateTime start =
              d.dispatchedAt().isAfter(period.from()) ? d.dispatchedAt() : period.from();
          OffsetDateTime end =
              d.deliveredAt().isBefore(period.to()) ? d.deliveredAt() : period.to();
          if (end.isAfter(start)) {
            intervalsByTruck
                .computeIfAbsent(d.truckId(), k -> new ArrayList<>())
                .add(new Interval(start, end));
          }
        }
      }
      long totalActiveSeconds = 0;
      for (List<Interval> intervals : intervalsByTruck.values()) {
        totalActiveSeconds += mergedDurationSeconds(intervals);
      }
      fleetUtilizationPct =
          MetricValue.of(
              BigDecimal.valueOf(totalActiveSeconds)
                  .multiply(BigDecimal.valueOf(100))
                  .divide(
                      BigDecimal.valueOf(totalScheduledSeconds),
                      2,
                      java.math.RoundingMode.HALF_UP));
    }

    // Loaded miles %: loaded distance vs total fleet mileage
    Mileage distance = totalMiles(period.from(), period.to());
    Double loadedMilesSum =
        loads.sumLoadedMiles(DELIVERED_LOAD_STATUSES, period.from(), period.to());
    MetricValue loadedMilesPct;
    if (distance.total() == null) {
      loadedMilesPct =
          MetricValue.unavailable(
              distance.readingRows() == 0
                  ? MetricUnavailableReason.NO_ODOMETER_SOURCE
                  : MetricUnavailableReason.NO_TOTAL_MILES_SOURCE);
    } else if (loadedMilesSum == null) {
      loadedMilesPct = MetricValue.of(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
    } else {
      BigDecimal share =
          BigDecimal.valueOf(loadedMilesSum)
              .multiply(BigDecimal.valueOf(100))
              .divide(distance.total(), 2, java.math.RoundingMode.HALF_UP);
      if (share.compareTo(BigDecimal.valueOf(100)) > 0) {
        share = BigDecimal.valueOf(100).setScale(2, java.math.RoundingMode.HALF_UP);
      }
      loadedMilesPct = MetricValue.of(share);
    }

    // DIFOT %: on time and in full (no defects and no unresolved exceptions)
    LoadDifotCounts difotCounts =
        loads.countDifot(DELIVERED_LOAD_STATUSES, period.from(), period.to());
    MetricValue difotPct =
        difotCounts == null
            ? MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS)
            : ReportingMetricsCalculator.percentage(
                difotCounts.difotCount(),
                difotCounts.comparableCount(),
                MetricUnavailableReason.NO_SOURCE_ROWS);

    Map<String, List<GroupCount>> distributions = new LinkedHashMap<>();
    distributions.put("truckStatus", trucks.groupByStatus());
    distributions.put("customerStatus", customers.groupByStatus());
    distributions.put("loadStatus", loads.groupByStatus());

    List<String> unavailable = new ArrayList<>();
    if (!fleetUtilizationPct.available()) {
      unavailable.add("fleetUtilizationPct");
    }
    if (!loadedMilesPct.available()) {
      unavailable.add("loadedMilesPct");
    }
    if (!onTimeDeliveryPct.available()) {
      unavailable.add("onTimeDeliveryPct");
    }
    if (!difotPct.available()) {
      unavailable.add("difotPct");
    }
    if (!trucksWithLoadsPct.available()) {
      unavailable.add("trucksWithLoadsPct");
    }

    return new ExecutiveSummaryResponse(
        ReportingMetricsCalculator.count(fleetSize),
        ReportingMetricsCalculator.count(activeCustomers),
        fleetUtilizationPct,
        loadedMilesPct,
        onTimeDeliveryPct,
        difotPct,
        trucksWithLoadsPct,
        new DataCompleteness(
            currency,
            Map.of(
                "trucks",
                fleetSize,
                "customers",
                activeCustomers,
                "loadsDeliveredInWindow",
                loads.countDeliveredInWindow(period.from(), period.to()),
                "trucksWithActivity",
                (long) trucksWithLoads.size()),
            0L,
            List.of(),
            distributions,
            unavailable));
  }

  // ── Monthly financials ───────────────────────────────────────────────────────────────────────

  @Override
  public MonthlyFinancialsResponse monthlyFinancials(
      String currency, OffsetDateTime from, OffsetDateTime to) {
    ReportPeriodResolver.Period period = periodResolver.resolve(from, to);

    List<InvoiceMonthlyRevenue> revenueRows =
        invoices.findMonthlyRevenue(currency, REVENUE_STATUSES, period.from(), period.to());
    List<LoadMonthlyMiles> mileRows =
        loads.findMonthlyMiles(DELIVERED_LOAD_STATUSES, period.from(), period.to());
    List<ExpenseLine> expenseLines = expenses.findLines(currency, period.from(), period.to());

    Map<String, BigDecimal> revenueByMonth = new HashMap<>();
    Map<String, Long> invoicesByMonth = new HashMap<>();
    for (InvoiceMonthlyRevenue row : revenueRows) {
      String key = monthKey(row.year(), row.month());
      revenueByMonth.merge(key, row.revenue(), ReportServiceImpl::addNullable);
      invoicesByMonth.merge(key, row.invoiceCount(), Long::sum);
    }

    Map<String, BigDecimal> milesByMonth = new HashMap<>();
    for (LoadMonthlyMiles row : mileRows) {
      if (row.miles() != null) {
        milesByMonth.merge(
            monthKey(row.year(), row.month()), BigDecimal.valueOf(row.miles()), BigDecimal::add);
      }
    }

    // Cost is bucketed here rather than in SQL because a month's cost is only ever needed beside
    // that month's revenue, and the two arrive by different routes. One grouping in Java beats two
    // groupings in two dialects that then have to agree on where a month boundary falls.
    Map<String, BigDecimal> costByMonth = new HashMap<>();
    for (ExpenseLine line : expenseLines) {
      if (line.expenseDate() != null && line.amount() != null) {
        costByMonth.merge(
            ReportingMetricsCalculator.monthKey(line.expenseDate()),
            line.amount(),
            BigDecimal::add);
      }
    }

    TreeSet<String> months = new TreeSet<>();
    months.addAll(revenueByMonth.keySet());
    months.addAll(milesByMonth.keySet());
    months.addAll(costByMonth.keySet());

    List<MonthlyFinancialsResponse.Point> points = new ArrayList<>();
    for (String month : months) {
      BigDecimal revenue = revenueByMonth.get(month);
      BigDecimal cost = costByMonth.get(month);
      BigDecimal loadedMiles = milesByMonth.get(month);

      MetricValue revenueMetric =
          ReportingMetricsCalculator.money(revenue, MetricUnavailableReason.NO_ROWS_IN_CURRENCY);
      MetricValue costMetric =
          ReportingMetricsCalculator.money(cost, MetricUnavailableReason.NO_SOURCE_ROWS);
      MetricValue milesMetric =
          loadedMiles == null
              ? MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS)
              : MetricValue.of(loadedMiles);

      MetricValue revenuePerMile = ratePerMile(revenue, loadedMiles);
      MetricValue costPerMile = ratePerMile(cost, loadedMiles);

      points.add(
          new MonthlyFinancialsResponse.Point(
              month,
              label(month),
              revenueMetric,
              costMetric,
              ReportingMetricsCalculator.moneyDifference(revenueMetric, costMetric),
              revenuePerMile,
              costPerMile,
              // Unavailable whenever either side is, so it can never collapse into a restatement of
              // revenue per mile under a name that claims to be a margin.
              ReportingMetricsCalculator.difference(revenuePerMile, costPerMile),
              milesMetric,
              // Nothing records empty or deadhead miles, so there is no total-distance figure to
              // report. The key stays in the contract rather than being filled with loads.distance
              // under a name that means something wider.
              MetricValue.unavailable(MetricUnavailableReason.NO_TOTAL_MILES_SOURCE),
              // A statement about the calendar, not about accounting: no accounting_periods table
              // exists, so nothing here knows whether a period has been formally closed.
              YearMonth.parse(month).isBefore(YearMonth.from(period.to())),
              invoicesByMonth.getOrDefault(month, 0L)));
    }

    return new MonthlyFinancialsResponse(
        currency,
        period.from(),
        period.to(),
        points,
        new DataCompleteness(
            currency,
            Map.of(
                "invoiceMonths", (long) revenueRows.size(),
                "expenseLines", (long) expenseLines.size(),
                "deliveredLoadMonths", (long) mileRows.size()),
            invoices.countInOtherCurrency(currency, period.from(), period.to())
                + expenses.countInOtherCurrency(currency, period.from(), period.to()),
            invoices.findDistinctCurrencies(period.from(), period.to()),
            Map.of(
                "invoiceStatus", invoices.groupByStatus(period.from(), period.to()),
                "expenseStatus", expenses.groupByStatus(period.from(), period.to())),
            List.of("totalMiles")));
  }

  // ── Cost structure ───────────────────────────────────────────────────────────────────────────

  @Override
  public CostBreakdownResponse costsByCategory(
      String currency, OffsetDateTime from, OffsetDateTime to) {
    ReportPeriodResolver.Period period = periodResolver.resolve(from, to);

    List<ExpenseLine> current = expenses.findLines(currency, period.from(), period.to());
    List<ExpenseLine> previous =
        expenses.findLines(currency, period.previousFrom(), period.previousTo());

    BigDecimal currentMiles = totalMiles(period.from(), period.to()).total();
    BigDecimal previousMiles = totalMiles(period.previousFrom(), period.previousTo()).total();

    Map<CostCategory, List<ExpenseLine>> currentByCategory = byCategory(current);
    Map<CostCategory, List<ExpenseLine>> previousByCategory = byCategory(previous);

    // Only the current window totals. The previous window is here to give each category a
    // period-over-period comparison, not to produce a second total: nothing in the response asks
    // for
    // one, and a total computed but never returned is a number waiting to be misread.
    BigDecimal currentTotal = sumLineAmounts(current);

    List<CostBreakdownResponse.Category> categories = new ArrayList<>();
    for (CostCategory category : CostCategory.chartOrder()) {
      BigDecimal categoryCurrent = sumLineAmounts(currentByCategory.get(category));
      BigDecimal categoryPrevious = sumLineAmounts(previousByCategory.get(category));

      // An empty category is a real zero, not an absent measurement: the buckets partition every
      // cost row, so a category with no rows is a category the business does not spend on.
      // Reporting it as unavailable would make an unused category look like a missing feed.
      categories.add(
          new CostBreakdownResponse.Category(
              category.id(),
              category.labelKey(),
              MetricValue.of(orZero(categoryCurrent)),
              MetricValue.of(orZero(categoryPrevious)),
              ratePerMile(categoryCurrent, currentMiles),
              ratePerMile(categoryPrevious, previousMiles),
              ReportingMetricsCalculator.share(categoryCurrent, currentTotal),
              currentByCategory.getOrDefault(category, List.of()).size()));
    }

    return new CostBreakdownResponse(
        currency,
        period.from(),
        period.to(),
        ReportingMetricsCalculator.money(currentTotal, MetricUnavailableReason.NO_SOURCE_ROWS),
        ambiguousTruckLinks(current),
        categories,
        new DataCompleteness(
            currency,
            Map.of(
                "expenseLines", (long) current.size(),
                "expenseLinesPrevious", (long) previous.size()),
            expenses.countInOtherCurrency(currency, period.from(), period.to()),
            List.copyOf(expenses.findDistinctCurrencies(period.from(), period.to())),
            Map.of("expenseStatus", expenses.groupByStatus(period.from(), period.to())),
            List.of()));
  }

  // ── Fleet health ─────────────────────────────────────────────────────────────────────────────

  @Override
  public FleetHealthResponse fleetHealth(String currency, OffsetDateTime from, OffsetDateTime to) {
    ReportPeriodResolver.Period period = periodResolver.resolve(from, to);

    long scheduled = maintenanceSchedules.countActiveWithDueDate();
    long notPastDue = maintenanceSchedules.countActiveWithDueDateNotPast(period.to());
    long requiringOdometer = maintenanceSchedules.countActiveMileageBasedWithoutTarget();

    long fleetSize = trucks.count();
    long windowSeconds = Duration.between(period.from(), period.to()).toSeconds();
    long totalFleetSeconds = fleetSize * windowSeconds;

    MetricValue unplannedDowntimePct;
    if (maintenanceRecords.countWithDowntimeInterval() == 0) {
      unplannedDowntimePct = MetricValue.unavailable(MetricUnavailableReason.NO_DOWNTIME_INTERVALS);
    } else if (totalFleetSeconds <= 0) {
      unplannedDowntimePct = MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    } else {
      List<DowntimeInterval> downtimes =
          maintenanceRecords.findUnplannedDowntimes(period.from(), period.to());
      Map<UUID, List<Interval>> intervalsByTruck = new HashMap<>();
      if (downtimes != null) {
        for (DowntimeInterval d : downtimes) {
          OffsetDateTime start =
              d.downtimeStartAt().isAfter(period.from()) ? d.downtimeStartAt() : period.from();
          OffsetDateTime end =
              d.downtimeEndAt() != null && d.downtimeEndAt().isBefore(period.to())
                  ? d.downtimeEndAt()
                  : period.to();
          if (end.isAfter(start)) {
            intervalsByTruck
                .computeIfAbsent(d.truckId(), k -> new ArrayList<>())
                .add(new Interval(start, end));
          }
        }
      }
      long totalDowntimeSeconds = 0;
      for (List<Interval> intervals : intervalsByTruck.values()) {
        totalDowntimeSeconds += mergedDurationSeconds(intervals);
      }
      unplannedDowntimePct =
          MetricValue.of(
              BigDecimal.valueOf(totalDowntimeSeconds)
                  .multiply(BigDecimal.valueOf(100))
                  .divide(
                      BigDecimal.valueOf(totalFleetSeconds), 2, java.math.RoundingMode.HALF_UP));
    }

    MetricValue pmCompliancePct =
        ReportingMetricsCalculator.percentage(
            notPastDue, scheduled, MetricUnavailableReason.NO_PM_SCHEDULE);

    BigDecimal maintenanceCost =
        maintenanceRecords.sumTotalCost(currency, period.from(), period.to());
    Mileage distance = totalMiles(period.from(), period.to());

    MetricValue maintenanceCostPerMile;
    if (maintenanceCost == null) {
      maintenanceCostPerMile = MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
    } else if (distance.total() == null) {
      maintenanceCostPerMile =
          MetricValue.unavailable(
              distance.readingRows() == 0
                  ? MetricUnavailableReason.NO_ODOMETER_SOURCE
                  : MetricUnavailableReason.NO_TOTAL_MILES_SOURCE);
    } else {
      maintenanceCostPerMile =
          ReportingMetricsCalculator.perMile(maintenanceCost, distance.total());
    }

    long breakdownCount = maintenanceRecords.countBreakdowns(period.from(), period.to());
    MetricValue breakdownsPer100kMiles;
    if (distance.total() == null) {
      breakdownsPer100kMiles =
          MetricValue.unavailable(
              distance.readingRows() == 0
                  ? MetricUnavailableReason.NO_ODOMETER_SOURCE
                  : MetricUnavailableReason.NO_TOTAL_MILES_SOURCE);
    } else {
      breakdownsPer100kMiles =
          ReportingMetricsCalculator.perHundredThousand(breakdownCount, distance.total());
    }

    List<String> unavailable = new ArrayList<>();
    if (!unplannedDowntimePct.available()) {
      unavailable.add("unplannedDowntimePct");
    }
    if (!pmCompliancePct.available()) {
      unavailable.add("pmCompliancePct");
    }
    if (!maintenanceCostPerMile.available()) {
      unavailable.add("maintenanceCostPerMile");
    }
    if (!breakdownsPer100kMiles.available()) {
      unavailable.add("breakdownsPer100kMiles");
    }

    return new FleetHealthResponse(
        currency,
        period.from(),
        period.to(),
        unplannedDowntimePct,
        pmCompliancePct,
        maintenanceCostPerMile,
        breakdownsPer100kMiles,
        requiringOdometer,
        new DataCompleteness(
            currency,
            Map.of(
                "maintenanceSchedulesJudged", scheduled,
                "maintenanceSchedulesRequiringOdometer", requiringOdometer,
                "maintenanceRecords", maintenanceRecords.countInWindow(period.from(), period.to()),
                "maintenanceRecordsWithoutCurrency",
                    maintenanceRecords.countWithoutCurrency(period.from(), period.to()),
                "maintenanceRecordsUnplanned",
                    maintenanceRecords.countUnplanned(period.from(), period.to()),
                "maintenanceRecordsUnclassified",
                    maintenanceRecords.countUnclassified(period.from(), period.to()),
                "odometerReadings", distance.readingRows(),
                "trucksWithOdometer", trucks.countWithOdometer()),
            maintenanceRecords.countInOtherCurrency(currency, period.from(), period.to()),
            List.of(currency),
            Map.of(
                "maintenanceType", maintenanceRecords.groupByType(period.from(), period.to()),
                "scheduleIntervalType", maintenanceSchedules.groupByIntervalType()),
            unavailable));
  }

  // ── Customer concentration ───────────────────────────────────────────────────────────────────

  @Override
  public CustomerConcentrationResponse customerConcentration(
      String currency, OffsetDateTime from, OffsetDateTime to, int limit) {
    ReportPeriodResolver.Period period = periodResolver.resolve(from, to);

    // Every customer, no limit. The shares and the index are computed from this list: truncating it
    // first would not change the largest customer's share, but it would change the index, which is
    // the one figure on this endpoint a client cannot recompute from the rows it was given.
    List<InvoiceCustomerRevenue> revenues =
        invoices.findRevenueByCustomer(currency, REVENUE_STATUSES, period.from(), period.to());
    List<BigDecimal> allRevenues = revenues.stream().map(InvoiceCustomerRevenue::revenue).toList();
    BigDecimal total = sumAmounts(allRevenues);

    Map<UUID, LoadCustomerStats> loadStats = new HashMap<>();
    for (LoadCustomerStats stats :
        loads.findCustomerStats(DELIVERED_LOAD_STATUSES, period.from(), period.to())) {
      loadStats.put(stats.customerId(), stats);
    }
    Map<UUID, BigDecimal> outstanding = outstandingByCustomer(currency);
    long daysInWindow = Math.max(Duration.between(period.from(), period.to()).toDays(), 1);

    int returned = Math.min(Math.max(limit, 1), revenues.size());
    List<CustomerConcentrationResponse.Row> rows = new ArrayList<>();
    for (InvoiceCustomerRevenue row : revenues.subList(0, returned)) {
      LoadCustomerStats stats = loadStats.get(row.customerId());
      BigDecimal miles = stats == null ? null : milesOf(stats.miles());

      rows.add(
          new CustomerConcentrationResponse.Row(
              row.customerId(),
              row.customerName(),
              ReportingMetricsCalculator.money(
                  row.revenue(), MetricUnavailableReason.NO_ROWS_IN_CURRENCY),
              ReportingMetricsCalculator.share(row.revenue(), total),
              ratePerMile(row.revenue(), miles),
              // Margin needs cost attributed to a customer, and nothing in this schema links an
              // expense to one: expenses carry a vehicle, not a shipment. Permanent, not a feed.
              MetricValue.unavailable(MetricUnavailableReason.NO_COST_ALLOCATION),
              stats == null
                  ? MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS)
                  : ReportingMetricsCalculator.percentage(
                      stats.onTimeCount(),
                      stats.comparableCount(),
                      MetricUnavailableReason.NO_SOURCE_ROWS),
              daysSalesOutstanding(
                  outstanding.get(row.customerId()), row.revenue(), daysInWindow)));
    }

    return new CustomerConcentrationResponse(
        currency,
        period.from(),
        period.to(),
        ReportingMetricsCalculator.topNShare(allRevenues, 1),
        ReportingMetricsCalculator.topNShare(allRevenues, 3),
        ReportingMetricsCalculator.topNShare(allRevenues, 5),
        // Always server-side and always over every customer. A client that recomputed this from
        // `rows` would be measuring whatever `limit` happened to be.
        ReportingMetricsCalculator.herfindahlIndex(allRevenues),
        revenues.size(),
        rows.size(),
        invoices.countRevenueBearingWithoutCustomer(
            currency, REVENUE_STATUSES, period.from(), period.to()),
        rows,
        new DataCompleteness(
            currency,
            Map.of("invoicesWithCustomer", (long) revenues.size()),
            invoices.countInOtherCurrency(currency, period.from(), period.to()),
            invoices.findDistinctCurrencies(period.from(), period.to()),
            Map.of("invoiceStatus", invoices.groupByStatus(period.from(), period.to())),
            List.of("grossMarginPercent")));
  }

  // ── Receivables ageing ───────────────────────────────────────────────────────────────────────

  @Override
  public ReceivablesAgingResponse receivablesAging(
      String currency, OffsetDateTime asOf, OffsetDateTime from, OffsetDateTime to) {
    ReportPeriodResolver.Period period = periodResolver.resolve(from, to);
    OffsetDateTime measuredAt = asOf != null ? asOf : periodResolver.now();

    List<InvoiceBalance> balances = invoices.findRevenueBearingBalances(currency, REVENUE_STATUSES);
    Map<UUID, BigDecimal> settledByInvoice = new HashMap<>();
    for (InvoicePayments settled : payments.sumSettledByInvoice(currency, SETTLED_STATUSES)) {
      settledByInvoice.merge(settled.invoiceId(), settled.paidAmount(), BigDecimal::add);
    }

    Map<AgingBucket, BigDecimal> byBucket = new EnumMap<>(AgingBucket.class);
    Map<AgingBucket, Long> countByBucket = new EnumMap<>(AgingBucket.class);
    BigDecimal outstandingTotal = BigDecimal.ZERO;
    BigDecimal overdueTotal = BigDecimal.ZERO;
    long mismatches = 0;

    for (InvoiceBalance balance : balances) {
      BigDecimal settled = settledByInvoice.getOrDefault(balance.invoiceId(), BigDecimal.ZERO);
      BigDecimal outstanding = balance.totalAmount().subtract(settled);

      // Two independent answers to "is this invoice settled": what the payments say, and what the
      // status word says. Where they disagree the invoice is counted, not corrected — the status
      // column is legacy free text this repository already writes in two casings, and silently
      // preferring one source would hide exactly the rows a reader should go and look at.
      boolean settledByPayments = outstanding.signum() <= 0;
      boolean settledByStatus = InvoiceStatus.fromDbValue(balance.status()) == InvoiceStatus.PAID;
      if (settledByPayments != settledByStatus) {
        mismatches++;
      }
      if (settledByPayments) {
        continue;
      }

      outstandingTotal = outstandingTotal.add(outstanding);

      AgingBucket bucket = AgingBucket.of(balance.dueDate(), measuredAt);
      byBucket.merge(bucket, outstanding, BigDecimal::add);
      countByBucket.merge(bucket, 1L, Long::sum);

      // A row with no due date is in its own band and contributes nothing to the overdue total. It
      // has not been shown to be current, and it has not been shown to be overdue either.
      if (balance.dueDate() != null && balance.dueDate().isBefore(measuredAt)) {
        overdueTotal = overdueTotal.add(outstanding);
      }
    }

    boolean anyInvoice = !balances.isEmpty();
    MetricValue outstandingMetric = moneyOrUnavailable(outstandingTotal, anyInvoice);
    BigDecimal windowRevenue =
        invoices.sumRevenue(currency, REVENUE_STATUSES, period.from(), period.to());
    long daysInWindow = Math.max(Duration.between(period.from(), period.to()).toDays(), 1);

    List<ReceivablesAgingResponse.Bucket> buckets = new ArrayList<>();
    for (AgingBucket bucket : AgingBucket.reportOrder()) {
      BigDecimal amount = byBucket.get(bucket);
      buckets.add(
          new ReceivablesAgingResponse.Bucket(
              bucket.id(),
              bucket.labelKey(),
              moneyOrUnavailable(orZero(amount), anyInvoice),
              ReportingMetricsCalculator.share(amount, anyInvoice ? outstandingTotal : null),
              countByBucket.getOrDefault(bucket, 0L)));
    }

    return new ReceivablesAgingResponse(
        currency,
        measuredAt,
        period.from(),
        period.to(),
        outstandingMetric,
        // Unavailable rather than zero when the window sold nothing: 40 days of sales outstanding
        // over no sales is not a fast collection cycle, it is an unanswerable question.
        // MetricValue.ratio owns that rule, and an unavailable outstanding balance passes through
        // unchanged rather than being divided.
        outstandingMetric.available()
            ? MetricValue.ratio(
                outstandingMetric.value().multiply(BigDecimal.valueOf(daysInWindow)),
                windowRevenue,
                DSO_SCALE)
            : outstandingMetric,
        moneyOrUnavailable(overdueTotal, anyInvoice),
        buckets,
        mismatches,
        new DataCompleteness(
            currency,
            Map.of(
                "revenueBearingInvoices",
                (long) balances.size(),
                "paymentsWithoutInvoice",
                payments.countUnapplied(currency, SETTLED_STATUSES)),
            invoices.countInOtherCurrency(currency, period.from(), period.to()),
            invoices.findDistinctCurrencies(period.from(), period.to()),
            Map.of("invoiceStatus", invoices.groupByStatus(period.from(), period.to())),
            List.of()));
  }

  // ── Shared helpers ───────────────────────────────────────────────────────────────────────────

  /**
   * Outstanding balance per customer, across every revenue-bearing invoice they have ever been
   * billed for — not only the ones inside the revenue window.
   *
   * <p>Receivables is a position rather than a flow: an invoice issued two years ago and never paid
   * is still owed today, so narrowing by date would hide exactly the debt this is measuring.
   */
  private Map<UUID, BigDecimal> outstandingByCustomer(String currency) {
    Map<UUID, BigDecimal> settledByInvoice = new HashMap<>();
    for (InvoicePayments settled : payments.sumSettledByInvoice(currency, SETTLED_STATUSES)) {
      settledByInvoice.merge(settled.invoiceId(), settled.paidAmount(), BigDecimal::add);
    }
    Map<UUID, BigDecimal> outstanding = new HashMap<>();
    for (InvoiceBalance balance : invoices.findRevenueBearingBalances(currency, REVENUE_STATUSES)) {
      if (balance.customerId() == null) {
        continue;
      }
      BigDecimal remaining =
          balance
              .totalAmount()
              .subtract(settledByInvoice.getOrDefault(balance.invoiceId(), BigDecimal.ZERO));
      if (remaining.signum() > 0) {
        outstanding.merge(balance.customerId(), remaining, BigDecimal::add);
      }
    }
    return outstanding;
  }

  /**
   * A monetary rate per mile, naming the missing denominator when there is none.
   *
   * <p>Distinct from {@link ReportingMetricsCalculator#perMile}, which reports every unusable
   * denominator as {@code ZERO_DENOMINATOR}. Here "no odometer readings exist for the window" and
   * "the readings that exist show no movement" are different findings, and the first is the one a
   * reader can go and act on.
   */
  private static MetricValue ratePerMile(BigDecimal amount, BigDecimal miles) {
    if (miles == null) {
      return MetricValue.unavailable(MetricUnavailableReason.NO_TOTAL_MILES_SOURCE);
    }
    return ReportingMetricsCalculator.perMile(amount, miles);
  }

  /**
   * Days sales outstanding: how many days of revenue a balance represents.
   *
   * <p>Derived from money actually received rather than from the status column, so it does not
   * inherit the casing problem. A customer who owes nothing has no DSO to report — not a DSO of
   * zero — so an absent balance short-circuits before the division.
   */
  private static MetricValue daysSalesOutstanding(
      BigDecimal outstanding, BigDecimal revenueInWindow, long daysInWindow) {
    if (outstanding == null || revenueInWindow == null) {
      return MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
    }
    return MetricValue.ratio(
        outstanding.multiply(BigDecimal.valueOf(daysInWindow)), revenueInWindow, DSO_SCALE);
  }

  /**
   * Total distance driven in the window, from the spread of odometer readings per truck.
   *
   * <p>Never summed across trucks at the reading level: odometer counters are cumulative and each
   * one starts wherever its vehicle's starts, so only a per-truck spread is a distance. Trucks with
   * a single reading are excluded and counted — a spread of zero there means "one data point", not
   * "did not move", and folding those zeros in would report a stationary fleet. A negative spread
   * is a counter that went backwards, which is a data fault rather than a distance, and is excluded
   * the same way.
   */
  private Mileage totalMiles(OffsetDateTime from, OffsetDateTime to) {
    BigDecimal total = null;
    long contributing = 0;
    for (TruckDistance spread : mileage.findReadingSpreadByTruck(from, to)) {
      if (spread.readingCount() < 2
          || spread.lowestReading() == null
          || spread.highestReading() == null) {
        continue;
      }
      long distance = (long) spread.highestReading() - spread.lowestReading();
      if (distance < 0) {
        continue;
      }
      BigDecimal asMiles = BigDecimal.valueOf(distance);
      total = addNullable(total, asMiles);
      contributing++;
    }
    return new Mileage(total, contributing, mileage.countInWindow(from, to));
  }

  /** Expenses grouped by the category the client charts them under. */
  private static Map<CostCategory, List<ExpenseLine>> byCategory(List<ExpenseLine> lines) {
    Map<CostCategory, List<ExpenseLine>> grouped = new EnumMap<>(CostCategory.class);
    for (ExpenseLine line : lines) {
      grouped
          .computeIfAbsent(
              CostCategory.classify(line.type(), line.category(), line.truckExpenseCategory()),
              category -> new ArrayList<>())
          .add(line);
    }
    return grouped;
  }

  /**
   * Expense rows whose two vehicle foreign keys name different trucks.
   *
   * <p>Counted in Java from the rows already loaded, not by a second query. The number a reader
   * needs is how many rows behind <em>this</em> chart carry contradictory links, and a separate
   * query without the currency predicate would count a different set — it would include rows whose
   * cost is not in the chart at all. Each row is visited once, so a row with both keys set is
   * counted once rather than twice.
   */
  private static long ambiguousTruckLinks(List<ExpenseLine> lines) {
    long ambiguous = 0;
    for (ExpenseLine line : lines) {
      if (line.truckId() != null
          && line.truckExpenseTruckId() != null
          && !line.truckId().equals(line.truckExpenseTruckId())) {
        ambiguous++;
      }
    }
    return ambiguous;
  }

  /** The summed amount of a set of expense lines, or {@code null} when none carries one. */
  private static BigDecimal sumLineAmounts(List<ExpenseLine> lines) {
    if (lines == null) {
      return null;
    }
    BigDecimal total = null;
    for (ExpenseLine line : lines) {
      if (line.amount() != null) {
        total = addNullable(total, line.amount());
      }
    }
    return total;
  }

  /** The sum of every non-null amount, or {@code null} when none was supplied. */
  private static BigDecimal sumAmounts(List<BigDecimal> amounts) {
    BigDecimal total = null;
    for (BigDecimal amount : amounts) {
      if (amount != null) {
        total = addNullable(total, amount);
      }
    }
    return total;
  }

  private static BigDecimal addNullable(BigDecimal left, BigDecimal right) {
    return left == null ? right : left.add(right);
  }

  /** A monetary total that is a real zero once the source table has been shown to have rows. */
  private static MetricValue moneyOrUnavailable(BigDecimal total, boolean anyRow) {
    return anyRow
        ? MetricValue.of(orZero(total))
        : MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static BigDecimal milesOf(Double miles) {
    return miles == null ? null : BigDecimal.valueOf(miles);
  }

  /** The {@code yyyy-MM} key for a month the database already grouped by. */
  private static String monthKey(int year, int month) {
    return YearMonth.of(year, month).toString();
  }

  /** A month rendered for a client that does not localise, so the chart has something to print. */
  private static String label(String month) {
    YearMonth parsed = YearMonth.parse(month);
    return String.format(Locale.ROOT, "%02d/%d", parsed.getMonthValue(), parsed.getYear());
  }

  /** Sums non-overlapping seconds across a set of intervals. */
  private static long mergedDurationSeconds(List<Interval> intervals) {
    if (intervals == null || intervals.isEmpty()) {
      return 0;
    }
    intervals.sort(Comparator.comparing(Interval::start));
    long total = 0;
    OffsetDateTime currentStart = intervals.get(0).start();
    OffsetDateTime currentEnd = intervals.get(0).end();

    for (int i = 1; i < intervals.size(); i++) {
      Interval next = intervals.get(i);
      if (!next.start().isAfter(currentEnd)) {
        if (next.end().isAfter(currentEnd)) {
          currentEnd = next.end();
        }
      } else {
        total += Duration.between(currentStart, currentEnd).toSeconds();
        currentStart = next.start();
        currentEnd = next.end();
      }
    }
    total += Duration.between(currentStart, currentEnd).toSeconds();
    return total;
  }

  private record Interval(OffsetDateTime start, OffsetDateTime end) {}

  /**
   * Distance driven in the window, and how much of the odometer table it was drawn from.
   *
   * @param total distance across every truck with at least two readings, or {@code null} when no
   *     truck has that many
   * @param contributingTrucks trucks that produced a distance
   * @param readingRows readings in the window, so a reader can see how many were consumed
   */
  private record Mileage(BigDecimal total, long contributingTrucks, long readingRows) {}
}
