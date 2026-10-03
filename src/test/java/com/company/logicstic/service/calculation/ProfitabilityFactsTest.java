package com.company.logicstic.service.calculation;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.exception.InvoiceReconciliationException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.profitability.ProfitabilityService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfitabilityFactsTest {
    final LoadRepository loads = mock(LoadRepository.class);
    final InvoiceRepository invoices = mock(InvoiceRepository.class);
    final ShipmentCostRepository costs = mock(ShipmentCostRepository.class);
    final AccessorialChargeRepository accessorials = mock(AccessorialChargeRepository.class);
    final TripStopRepository stops = mock(TripStopRepository.class);
    final ProfitabilityService service = new ProfitabilityService(loads, invoices, costs, accessorials,
            mock(com.company.logicstic.service.cost.ShipmentCostEngine.class),
            mock(com.company.logicstic.service.accessorial.AccessorialService.class), stops,
            TestRoundingPolicies.standard(), new InvoiceReconciliationService(TestRoundingPolicies.standard()),
            new com.company.logicstic.service.profitability.ProfitabilityCalculator(
                    new com.company.logicstic.service.profitability.DefaultCostClassificationPolicyV1(), TestRoundingPolicies.standard()));

    Load load(String currency) {
        Load load = new Load(); load.setId(UUID.randomUUID()); load.setDeliveryCostCurrency(currency);
        load.setDeliveryCostAmount(new BigDecimal("99999")); load.setDistance(99999d);
        load.setOriginAddressState("TX"); load.setDestinationAddressState("CA");
        when(loads.findById(load.getId())).thenReturn(Optional.of(load));
        return load;
    }
    Invoice invoice(Load load, String status, String amount) {
        Invoice invoice = new Invoice(); invoice.setId(UUID.randomUUID()); invoice.setStatus(status);
        invoice.setSubtotalCurrency(load.getDeliveryCostCurrency()); invoice.setSubtotalAmount(new BigDecimal(amount));
        InvoiceLineItem item = new InvoiceLineItem(); item.setAmountAmount(new BigDecimal(amount)); item.setAmountCurrency(load.getDeliveryCostCurrency());
        invoice.setLineItems(List.of(item));
        when(invoices.findByLoadId(load.getId())).thenReturn(Optional.of(invoice));
        return invoice;
    }
    ShipmentCost cost(String basis, String status, String amount, String currency) {
        ShipmentCost cost = new ShipmentCost(); cost.setCostBasis(basis); cost.setStatus(status);
        cost.setAmount(new BigDecimal(amount)); cost.setCurrency(currency); return cost;
    }
    Trip trip(Load load, String actual, String loaded, String empty) {
        Trip trip = new Trip(); trip.setId(UUID.randomUUID()); trip.setTotalDistance(999999d);
        trip.setActualDistanceMiles(actual == null ? null : new BigDecimal(actual));
        trip.setLoadedMiles(new BigDecimal(loaded)); trip.setEmptyMiles(new BigDecimal(empty));
        TripStop stop = new TripStop(); stop.setTrip(trip); stop.setLoad(load);
        when(stops.findByLoadId(load.getId())).thenReturn(List.of(stop));
        when(stops.findByTripIdOrderByOrderAsc(trip.getId())).thenReturn(List.of(stop));
        return trip;
    }

    @Test void quoteNeverBecomesActualRevenueAndEmptyCostsHaveZeroProfitButNoPercentage() {
        Load load = load("USD");
        var report = service.byLoad(load.getId(), null);
        assertEquals(new BigDecimal("0.00"), report.actualRevenue()); assertEquals(new BigDecimal("0.00"), report.allocatedProfit());
        assertEquals(new BigDecimal("0.00"), report.contributionMargin()); assertNull(report.estimatedCost());
        assertEquals("ZERO_REVENUE_DENOMINATOR", report.marginPercent().reason());
        assertEquals("NO_AUTHORITATIVE_COST_ESTIMATE", report.costVariance().reason());
    }
    @Test void excludesEveryNonRevenueInvoiceStatusIncludingRejectedAndUnknown() {
        Load load = load("USD");
        for (String status : List.of("DRAFT", "CANCELLED", "PENDING_APPROVAL", "APPROVED", "REJECTED", "UNKNOWN")) {
            Invoice invoice = invoice(load, status, "100"); invoice.setSubtotalCurrency("VND");
            assertEquals(new BigDecimal("0.00"), service.byLoad(load.getId(), null).actualRevenue());
        }
    }
    @Test void reconcilesEligibleRevenueAndRejectsMixedSourceOrRequestedCurrency() {
        Load load = load("USD"); Invoice invoice = invoice(load,"ISSUED","100");
        invoice.getLineItems().getFirst().setAmountAmount(new BigDecimal("99"));
        assertThrows(InvoiceReconciliationException.class, () -> service.byLoad(load.getId(),null));
        invoice.getLineItems().getFirst().setAmountAmount(new BigDecimal("100"));
        assertThrows(CurrencyMismatchException.class, () -> service.byLoad(load.getId(),"VND"));
        invoice.setSubtotalCurrency("VND");
        assertThrows(CurrencyMismatchException.class, () -> service.byLoad(load.getId(),null));
    }
    @Test void actualStagesCreditsAndAuthoritativeEstimatesHaveIndependentDimensions() {
        Load load = load("USD"); invoice(load,"PAID","100");
        when(costs.findByLoadId(load.getId())).thenReturn(List.of(cost("ACTUAL","APPROVED","40","USD"),
                cost("ACTUAL","POSTED","-10","USD"), cost("ACTUAL","VERIFIED","999","VND"),
                cost("ACTUAL","VOIDED","999","VND"), cost("ESTIMATE","APPROVED","20","USD"),
                cost("ESTIMATE","VOIDED","999","VND"), cost("ESTIMATE","VERIFIED","999","USD"),
                cost("ACCRUAL","APPROVED","999","USD")));
        var result = service.byLoad(load.getId(),null);
        assertEquals(new BigDecimal("30.00"), result.actualCost()); assertEquals(new BigDecimal("20.00"), result.estimatedCost());
        assertEquals(new BigDecimal("10.00"), result.costVariance().value());
    }
    @Test void explicitMileageProducesUnitEconomicsWithoutLegacyDistance() {
        Load load = load("USD"); invoice(load,"SENT","100"); trip(load,"100","60","40");
        when(costs.findByLoadId(load.getId())).thenReturn(List.of(cost("ACTUAL","POSTED","20","USD")));
        var report = service.byLoad(load.getId(),null);
        assertEquals(new BigDecimal("1.0000"), report.revenuePerTotalMile().value());
        assertEquals(new BigDecimal("0.2000"), report.costPerTotalMile().value());
        assertEquals(new BigDecimal("0.3333"), report.breakEvenLoadedRate().value());
    }
    @Test void missingTotalMileageCannotPublishBreakEvenFromLoadedSubset() {
        Load load = load("USD"); trip(load,null,"60","40");
        assertEquals(MetricAvailability.UNAVAILABLE, service.byLoad(load.getId(),null).breakEvenLoadedRate().availability());
    }
    @Test void sharedTripRejectsAllLoadMileageWithoutGuessingProration() {
        Load load = load("USD"); Trip trip = trip(load,"100","60","40");
        TripStop other = new TripStop(); other.setLoad(load("USD")); other.setTrip(trip);
        var original = stops.findByLoadId(load.getId()).getFirst();
        when(stops.findByTripIdOrderByOrderAsc(trip.getId())).thenReturn(List.of(original,other));
        assertEquals("TRIP_MILEAGE_CANNOT_BE_ATTRIBUTED_ACROSS_MULTIPLE_LOADS",
                service.byLoad(load.getId(),null).breakEvenLoadedRate().reason());
    }
    @Test void mileagePartitionMustReconcileBeforeBreakEvenIsAvailable() {
        Load load = load("USD"); trip(load,"100","80","80");
        var report = service.byLoad(load.getId(),null);
        assertEquals(MetricAvailability.AVAILABLE, report.revenuePerTotalMile().availability());
        assertEquals("LOADED_EMPTY_MILES_DO_NOT_RECONCILE", report.breakEvenLoadedRate().reason());
    }
    @Test void lanesArePartitionedByCurrencyAndNeverAddQuotes() {
        Load usd = load("USD"), vnd = load("VND"); invoice(usd,"ISSUED","100"); invoice(vnd,"ISSUED","500000");
        when(loads.findAll()).thenReturn(List.of(usd,vnd));
        var reports = service.getLaneProfitabilityReports(); assertEquals(2,reports.size());
        assertEquals(new BigDecimal("100.00"), reports.stream().filter(r -> r.currency().equals("USD")).findFirst().orElseThrow().totalRevenue());
        assertEquals(new BigDecimal("500000"), reports.stream().filter(r -> r.currency().equals("VND")).findFirst().orElseThrow().totalRevenue());
        assertTrue(reports.stream().allMatch(r -> r.totalProfit().compareTo(r.totalRevenue()) == 0
                && r.averageMarginPercent().compareTo(new BigDecimal("100")) == 0));
    }
    @Test void truckReportUsesTripTruckAndRetainsUnattributedBucket() {
        Load load = load("USD"), unknown = load("USD"); Trip trip = trip(load,"100","60","40");
        Truck old = new Truck(); old.setId(UUID.randomUUID()); old.setNumber("OLD"); trip.setTruck(old);
        Truck reassigned = new Truck(); reassigned.setId(UUID.randomUUID()); reassigned.setNumber("NEW"); load.setAssignedTruck(reassigned);
        when(loads.findAll()).thenReturn(List.of(load,unknown));
        var reports = service.getTruckProfitabilityReports(); assertEquals(2,reports.size());
        assertTrue(reports.stream().anyMatch(r -> old.getId().equals(r.truckId())));
        var bucket = reports.stream().filter(r -> r.truckId() == null).findFirst().orElseThrow();
        assertEquals(1,bucket.loadCount()); assertEquals(MetricAvailability.UNAVAILABLE,bucket.truckAttributionMetric().availability());
    }
    @Test void groupedAndAllLoadReportsNeverSilentlyDropCorruptInvoices() {
        Load load = load("USD"); Invoice invoice = invoice(load,"ISSUED","100"); invoice.setSubtotalCurrency("VND");
        when(loads.findAll()).thenReturn(List.of(load));
        assertThrows(CurrencyMismatchException.class, service::getAllLoadFinancialSummaries);
        assertThrows(CurrencyMismatchException.class, service::getLaneProfitabilityReports);
        assertThrows(CurrencyMismatchException.class, service::getTruckProfitabilityReports);
    }
}
