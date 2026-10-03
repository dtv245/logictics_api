package com.company.logicstic.service.profitability;

import com.company.logicstic.common.*;
import com.company.logicstic.common.enums.InvoiceStatus;
import com.company.logicstic.dto.profitability.*;
import com.company.logicstic.dto.report.LoadProfitabilityReport;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.accessorial.AccessorialService;
import com.company.logicstic.service.calculation.InvoiceReconciliationService;
import com.company.logicstic.service.cost.ShipmentCostEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Read-only financial facts. Unknown classification is never silently treated as variable cost. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ProfitabilityService {
    public static final String CLASSIFICATION_MISSING = ProfitabilityCalculator.CLASSIFICATION_INCOMPLETE;
    private final LoadRepository loadRepository;
    private final InvoiceRepository invoiceRepository;
    private final ShipmentCostRepository shipmentCostRepository;
    private final AccessorialChargeRepository accessorialChargeRepository;
    private final ShipmentCostEngine shipmentCostEngine;
    private final AccessorialService accessorialService;
    private final TripStopRepository tripStopRepository;
    private final FinancialRoundingPolicy rounding;
    private final InvoiceReconciliationService reconciliation;
    private final ProfitabilityCalculator calculator;

    public LoadFinancialSummary getLoadFinancialSummary(UUID loadId) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));
        String currency = CurrencyGuard.normalize(load.getDeliveryCostCurrency());
        BigDecimal revenue = BigDecimal.ZERO;
        for (Invoice invoice : invoiceRepository.findByLoadId(loadId).stream().toList()) {
            if (!InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue()) continue;
            if (invoice.getSubtotalAmount() == null) throw new BadRequestException("INVOICE_SUBTOTAL_MISSING", "Eligible invoice requires a subtotal");
            CurrencyGuard.requireSameCurrency(currency, invoice.getSubtotalCurrency());
            reconciliation.reconcile(invoice);
            revenue = revenue.add(invoice.getSubtotalAmount());
        }
        revenue = money(revenue, currency);
        BigDecimal quote = money(load.getDeliveryCostAmount(), currency); // Context only, never actual revenue.
        List<ShipmentCost> costs = shipmentCostRepository.findByLoadId(loadId);
        BigDecimal actual = BigDecimal.ZERO, estimate = BigDecimal.ZERO;
        boolean estimatePresent = false;
        for (ShipmentCost cost : costs) {
            if (!eligibleCost(cost)) continue;
            boolean actualBasis = "ACTUAL".equalsIgnoreCase(cost.getCostBasis());
            boolean estimateBasis = "ESTIMATE".equalsIgnoreCase(cost.getCostBasis());
            if (!actualBasis && !estimateBasis) continue;
            CurrencyGuard.requireSameCurrency(currency, cost.getCurrency());
            if (cost.getAmount() == null) throw new BadRequestException("SHIPMENT_COST_AMOUNT_MISSING", "Eligible cost requires an amount");
            if (actualBasis) actual = actual.add(cost.getAmount());
            else { estimate = estimate.add(cost.getAmount()); estimatePresent = true; }
        }
        BigDecimal rawActual = actual;
        actual = money(rawActual, currency);
        BigDecimal estimatedCost = estimatePresent ? money(estimate, currency) : null;
        BigDecimal variance = estimatePresent ? money(rawActual.subtract(estimate), currency) : null;
        MetricDto varianceMetric = estimatePresent
                ? MetricDto.available("COST_VARIANCE", variance, "CURRENCY", "ACTUAL_MINUS_APPROVED_ESTIMATE")
                : MetricDto.unavailable("COST_VARIANCE", "CURRENCY", "NO_AUTHORITATIVE_COST_ESTIMATE");
        ProfitabilityMileageMetrics mileage = mileageMetrics(loadId, revenue, actual);
        CostClassificationSummary classification = calculator.calculate(revenue, currency, costs);
        return new LoadFinancialSummary(loadId, load.getNumber() == null ? null : load.getNumber().toString(), currency,
                quote, revenue, actual, estimatedCost, variance, classification.contributionMargin().value(),
                classification.allocatedProfit().value(), legacyMargin(classification.allocatedMarginPercent()).value(),
                mileage.totalMiles().value(), mileage.loadedMiles().value(), mileage.emptyMiles().value(),
                mileage.revenuePerTotalMile().value(), mileage.costPerTotalMile().value(), mileage.breakEvenLoadedRate().value(),
                costs.stream().map(shipmentCostEngine::toView).toList(),
                accessorialChargeRepository.findByLoadId(loadId).stream().map(accessorialService::toView).toList(), mileage,
                classification.contributionMargin(), classification.allocatedProfit(),
                legacyMargin(classification.allocatedMarginPercent()), varianceMetric, classification);
    }

    public LoadProfitabilityReport byLoad(UUID loadId, String reportCurrency) {
        LoadFinancialSummary s = getLoadFinancialSummary(loadId);
        if (reportCurrency != null) CurrencyGuard.requireSameCurrency(s.currency(), reportCurrency);
        return new LoadProfitabilityReport(loadId, s.currency(), s.actualInvoicedRevenue(), s.actualCost(),
                s.contributionMargin(), s.allocatedProfit(), s.marginPercentMetric(), s.mileageMetrics().revenuePerTotalMile(),
                s.mileageMetrics().costPerTotalMile(), s.mileageMetrics().breakEvenLoadedRate(), s.estimatedCost(),
                s.costVarianceMetric(), s.contributionMarginMetric(), s.allocatedProfitMetric(), s.costClassification());
    }

    public List<LoadFinancialSummary> getAllLoadFinancialSummaries() {
        // Never hide invalid financial records behind catch-and-skip behavior.
        return loadRepository.findAll().stream().map(l -> getLoadFinancialSummary(l.getId())).toList();
    }

    private record LaneKey(String origin, String destination, String currency) {}
    public List<LaneProfitabilityReport> getLaneProfitabilityReports() {
        Map<LaneKey, List<LoadFinancialSummary>> groups = new LinkedHashMap<>();
        for (Load load : loadRepository.findAll()) {
            LoadFinancialSummary summary = getLoadFinancialSummary(load.getId());
            var key = new LaneKey(state(load.getOriginAddressState()), state(load.getDestinationAddressState()), summary.currency());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(summary);
        }
        List<LaneProfitabilityReport> reports = new ArrayList<>();
        groups.forEach((key, summaries) -> {
            Totals t = totals(summaries);
            MetricDto rpm = ratio("REVENUE_PER_MILE", t.revenue(), t.miles(), t.milesMetric().reason());
            CostClassificationSummary classification = calculator.aggregate(key.currency(), summaries.stream().map(LoadFinancialSummary::costClassification).toList());
            reports.add(new LaneProfitabilityReport(key.origin(), key.destination(), key.currency(), summaries.size(),
                    t.revenue(), t.cost(), classification.allocatedProfit().value(), legacyMargin(classification.allocatedMarginPercent()).value(),
                    t.miles(), rpm.value(), t.milesMetric(), rpm, classification.allocatedProfit(),
                    legacyMargin(classification.allocatedMarginPercent()), classification));
        });
        return reports;
    }

    private record TruckKey(UUID truckId, String currency) {}
    public List<TruckProfitabilityReport> getTruckProfitabilityReports() {
        Map<TruckKey, List<LoadFinancialSummary>> groups = new LinkedHashMap<>();
        Map<UUID, String> numbers = new HashMap<>();
        for (Load load : loadRepository.findAll()) {
            LoadFinancialSummary summary = getLoadFinancialSummary(load.getId());
            Truck truck = historicalTruck(load.getId());
            UUID id = truck == null ? null : truck.getId();
            if (truck != null) numbers.put(id, truck.getNumber());
            groups.computeIfAbsent(new TruckKey(id, summary.currency()), ignored -> new ArrayList<>()).add(summary);
        }
        List<TruckProfitabilityReport> reports = new ArrayList<>();
        groups.forEach((key, summaries) -> {
            Totals t = totals(summaries);
            MetricDto cpm = ratio("COST_PER_MILE", t.cost(), t.miles(), t.milesMetric().reason());
            CostClassificationSummary classification = calculator.aggregate(key.currency(), summaries.stream().map(LoadFinancialSummary::costClassification).toList());
            MetricDto attribution = key.truckId() == null
                    ? MetricDto.unavailable("TRUCK_ATTRIBUTION", null, "NO_SINGLE_HISTORICAL_TRUCK_ATTRIBUTION")
                    : MetricDto.available("TRUCK_ATTRIBUTION", null, null, "TRIP_TRUCK_HISTORY");
            reports.add(new TruckProfitabilityReport(key.truckId(), key.truckId() == null ? "UNALLOCATED" : numbers.get(key.truckId()),
                    key.currency(), summaries.size(), t.revenue(), t.cost(), classification.allocatedProfit().value(),
                    legacyMargin(classification.allocatedMarginPercent()).value(), t.miles(), cpm.value(), t.milesMetric(), cpm,
                    classification.allocatedProfit(), legacyMargin(classification.allocatedMarginPercent()), attribution, classification));
        });
        return reports;
    }

    private Truck historicalTruck(UUID loadId) {
        Map<UUID, Trip> trips = loadTrips(loadId);
        if (trips.isEmpty()) return null;
        Truck truck = null;
        for (Trip trip : trips.values()) {
            if (trip.getTruck() == null || (truck != null && !truck.getId().equals(trip.getTruck().getId()))) return null;
            truck = trip.getTruck();
        }
        return truck;
    }

    private record Totals(BigDecimal revenue, BigDecimal cost, BigDecimal miles, MetricDto milesMetric) {}
    private Totals totals(List<LoadFinancialSummary> summaries) {
        BigDecimal revenue = summaries.stream().map(LoadFinancialSummary::actualInvoicedRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = summaries.stream().map(LoadFinancialSummary::actualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean complete = summaries.stream().allMatch(s -> s.mileageMetrics().totalMiles().availability() == MetricAvailability.AVAILABLE);
        BigDecimal miles = complete ? summaries.stream().map(LoadFinancialSummary::totalMiles).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        MetricDto metric = complete ? MetricDto.available("TOTAL_MILES", miles, "MILE", "EXPLICIT_TRIP_MILEAGE_V3")
                : MetricDto.unavailable("TOTAL_MILES", "MILE", "ONE_OR_MORE_LOADS_HAVE_NO_UNAMBIGUOUS_EXPLICIT_MILEAGE");
        return new Totals(revenue, cost, miles, metric);
    }

    private Map<UUID, Trip> loadTrips(UUID loadId) {
        Map<UUID, Trip> trips = new LinkedHashMap<>();
        for (TripStop stop : tripStopRepository.findByLoadId(loadId)) {
            if (stop.getTrip() != null) trips.put(stop.getTrip().getId(), stop.getTrip());
        }
        return trips;
    }

    private ProfitabilityMileageMetrics mileageMetrics(UUID loadId, BigDecimal revenue, BigDecimal cost) {
        Map<UUID, Trip> trips = loadTrips(loadId);
        if (trips.isEmpty()) return unavailableMileage("LOAD_HAS_NO_TRIP_ATTRIBUTION");
        BigDecimal total = BigDecimal.ZERO, loaded = BigDecimal.ZERO, empty = BigDecimal.ZERO;
        boolean totalComplete = true, loadedComplete = true, emptyComplete = true;
        String reason = "EXPLICIT_LOAD_MILEAGE_NOT_AVAILABLE";
        for (Trip trip : trips.values()) {
            Set<UUID> attributedLoads = new HashSet<>();
            for (TripStop stop : tripStopRepository.findByTripIdOrderByOrderAsc(trip.getId())) {
                if (stop.getLoad() != null) attributedLoads.add(stop.getLoad().getId());
            }
            if (!attributedLoads.equals(Set.of(loadId))) return unavailableMileage("TRIP_MILEAGE_CANNOT_BE_ATTRIBUTED_ACROSS_MULTIPLE_LOADS");
            BigDecimal actual = trip.getActualDistanceMiles(), l = trip.getLoadedMiles(), e = trip.getEmptyMiles();
            if (!nonNegative(actual)) totalComplete = false; else total = total.add(actual);
            if (!nonNegative(l) || actual == null || l.compareTo(actual) > 0) loadedComplete = false; else loaded = loaded.add(l);
            if (!nonNegative(e) || actual == null || e.compareTo(actual) > 0) emptyComplete = false; else empty = empty.add(e);
            if (nonNegative(actual) && nonNegative(l) && nonNegative(e) && l.add(e).compareTo(actual) != 0) {
                loadedComplete = false; emptyComplete = false; reason = "LOADED_EMPTY_MILES_DO_NOT_RECONCILE";
            }
        }
        MetricDto totalMetric = totalComplete ? MetricDto.available("TOTAL_MILES", total, "MILE", "EXPLICIT_TRIP_MILEAGE_V3") : MetricDto.unavailable("TOTAL_MILES", "MILE", reason);
        MetricDto loadedMetric = totalComplete && loadedComplete ? MetricDto.available("LOADED_MILES", loaded, "MILE", "EXPLICIT_TRIP_MILEAGE_V3") : MetricDto.unavailable("LOADED_MILES", "MILE", reason);
        MetricDto emptyMetric = totalComplete && emptyComplete ? MetricDto.available("EMPTY_MILES", empty, "MILE", "EXPLICIT_TRIP_MILEAGE_V3") : MetricDto.unavailable("EMPTY_MILES", "MILE", reason);
        return new ProfitabilityMileageMetrics(totalMetric, loadedMetric, emptyMetric,
                ratio("REVENUE_PER_MILE", revenue, totalMetric.value(), reason), ratio("COST_PER_MILE", cost, totalMetric.value(), reason),
                ratio("BREAK_EVEN_LOADED_RATE", cost, loadedMetric.value(), reason));
    }

    private ProfitabilityMileageMetrics unavailableMileage(String reason) {
        return new ProfitabilityMileageMetrics(MetricDto.unavailable("TOTAL_MILES", "MILE", reason), MetricDto.unavailable("LOADED_MILES", "MILE", reason),
                MetricDto.unavailable("EMPTY_MILES", "MILE", reason), MetricDto.unavailable("REVENUE_PER_MILE", "CURRENCY_PER_MILE", reason),
                MetricDto.unavailable("COST_PER_MILE", "CURRENCY_PER_MILE", reason), MetricDto.unavailable("BREAK_EVEN_LOADED_RATE", "CURRENCY_PER_MILE", reason));
    }
    private MetricDto ratio(String code, BigDecimal numerator, BigDecimal denominator, String reason) {
        if (denominator == null) return MetricDto.unavailable(code, "CURRENCY_PER_MILE", reason);
        if (denominator.signum() == 0) return MetricDto.unavailable(code, "CURRENCY_PER_MILE", "ZERO_MILE_DENOMINATOR");
        return MetricDto.available(code, rounding.divide(numerator, denominator, 4, FinancialRoundingPolicy.Boundary.REPORT),
                "CURRENCY_PER_MILE", numerator, denominator, "EXPLICIT_TRIP_MILEAGE_V3");
    }
    private MetricDto legacyMargin(MetricDto metric) {
        BigDecimal percent = metric.value() == null ? null : rounding.divide(metric.numerator().multiply(BigDecimal.valueOf(100)),
                metric.denominator(), 4, FinancialRoundingPolicy.Boundary.REPORT);
        return new MetricDto("MARGIN_PERCENT", percent, "PERCENT", metric.numerator(), metric.denominator(),
                metric.availability(), metric.basis(), metric.reason());
    }
    private BigDecimal money(BigDecimal amount, String currency) { return rounding.money(amount, currency, FinancialRoundingPolicy.Boundary.REPORT); }
    private boolean nonNegative(BigDecimal amount) { return amount != null && amount.signum() >= 0; }
    private boolean eligibleCost(ShipmentCost cost) { return "APPROVED".equalsIgnoreCase(cost.getStatus()) || "POSTED".equalsIgnoreCase(cost.getStatus()); }
    private String state(String value) { return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT); }
}
