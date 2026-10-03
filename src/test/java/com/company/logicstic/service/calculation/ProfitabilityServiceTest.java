package com.company.logicstic.service.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.Load;
import com.company.logicstic.service.profitability.ProfitabilityService;
import com.company.logicstic.repository.AccessorialChargeRepository;
import com.company.logicstic.repository.TripStopRepository;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.ShipmentCostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfitabilityServiceTest {
    @Mock LoadRepository loads; @Mock InvoiceRepository invoices; @Mock ShipmentCostRepository costs;
    @Mock TripStopRepository stops;
    @Mock AccessorialChargeRepository accessorials;
    @Mock com.company.logicstic.service.cost.ShipmentCostEngine engine;
    @Mock com.company.logicstic.service.accessorial.AccessorialService accessorialService;
    @Mock InvoiceReconciliationService reconciliation;
    @Spy com.company.logicstic.common.FinancialRoundingPolicy rounding = TestRoundingPolicies.standard();
    @Spy com.company.logicstic.service.profitability.ProfitabilityCalculator calculator =
            new com.company.logicstic.service.profitability.ProfitabilityCalculator(
                    new com.company.logicstic.service.profitability.DefaultCostClassificationPolicyV1(), TestRoundingPolicies.standard());
    @InjectMocks ProfitabilityService service;
    @Test void usesOnlyApprovedActualLedgerCostsAndMarksMileageUnavailable() {
        UUID loadId = UUID.randomUUID(); Invoice invoice = new Invoice(); invoice.setStatus("ISSUED"); invoice.setSubtotalCurrency("USD"); invoice.setSubtotalAmount(new BigDecimal("100.00"));
        Load load = new Load(); load.setId(loadId); load.setDeliveryCostCurrency("USD");
        ShipmentCost cost = new ShipmentCost(); cost.setLoad(load); cost.setCurrency("USD"); cost.setAmount(new BigDecimal("40.00")); cost.setCostBasis("ACTUAL"); cost.setStatus("APPROVED");
        ShipmentCost verified = new ShipmentCost(); verified.setLoad(load); verified.setCurrency("USD"); verified.setAmount(new BigDecimal("70.00")); verified.setCostBasis("ACTUAL"); verified.setStatus("VERIFIED");
        when(loads.findById(loadId)).thenReturn(Optional.of(load)); when(invoices.findByLoadId(loadId)).thenReturn(Optional.of(invoice));
        when(costs.findByLoadId(loadId)).thenReturn(List.of(cost, verified));
        var result = service.byLoad(loadId, "USD");
        assertEquals(new BigDecimal("100.00"), result.actualRevenue()); assertEquals(new BigDecimal("40.00"), result.actualCost());
        org.junit.jupiter.api.Assertions.assertNull(result.contributionMargin());
        assertEquals(ProfitabilityService.CLASSIFICATION_MISSING, result.contributionMarginMetric().reason());
        assertEquals(MetricAvailability.UNAVAILABLE, result.revenuePerTotalMile().availability());
    }
    @Test void rejectsMixedCurrencies() {
        UUID loadId = UUID.randomUUID(); Invoice invoice = new Invoice(); invoice.setStatus("ISSUED"); invoice.setSubtotalCurrency("USD"); invoice.setSubtotalAmount(BigDecimal.ONE);
        Load load = new Load(); load.setId(loadId); load.setDeliveryCostCurrency("USD");
        when(loads.findById(loadId)).thenReturn(Optional.of(load)); when(invoices.findByLoadId(loadId)).thenReturn(Optional.of(invoice));
        assertThrows(CurrencyMismatchException.class, () -> service.byLoad(loadId, "VND"));
    }
}
