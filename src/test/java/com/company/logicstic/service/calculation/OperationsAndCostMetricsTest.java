package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.dto.report.FuelReport;
import com.company.logicstic.dto.report.OtdReport;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.MaintenanceRecord;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.repository.LoadExceptionRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.MaintenanceRecordRepository;
import com.company.logicstic.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsAndCostMetricsTest {

    @Mock
    private LoadRepository loadRepository;
    @Mock
    private LoadExceptionRepository loadExceptionRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock
    private TripRepository tripRepository;

    private OperationsMetricsService operationsMetricsService;
    private OperatingCostCalculator operatingCostCalculator;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        var rounding = TestRoundingPolicies.standard();
        operationsMetricsService = new OperationsMetricsService(new OnTimeCalculator(loadRepository, rounding),
                new DelayCalculator(loadRepository, rounding), new ExceptionMetricsService(loadExceptionRepository, rounding));
        operatingCostCalculator = new OperatingCostCalculator(expenseRepository, tripRepository,
                new ExpenseReportService(expenseRepository, rounding), new MaintenanceReportService(maintenanceRecordRepository), rounding);
    }

    @Test
    @DisplayName("Calculate OTD accurately with on-time and delayed loads")
    void testCalculateOtd() {
        OffsetDateTime deadline1 = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime actual1 = OffsetDateTime.of(2026, 10, 1, 11, 0, 0, 0, ZoneOffset.UTC); // On-time

        OffsetDateTime deadline2 = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime actual2 = OffsetDateTime.of(2026, 10, 1, 14, 0, 0, 0, ZoneOffset.UTC); // 2 hours late (120 mins)

        Load load1 = new Load();
        load1.setRequestedDeliveryDate(deadline1);
        load1.setDeliveredAt(actual1);

        Load load2 = new Load();
        load2.setRequestedDeliveryDate(deadline2);
        load2.setDeliveredAt(actual2);

        when(loadRepository.findAll()).thenReturn(List.of(load1, load2));

        OtdReport report = operationsMetricsService.calculateOtd(null, null);

        assertNotNull(report);
        assertEquals(2, report.totalEligibleLoads());
        assertEquals(1, report.onTimeLoads());
        assertEquals(new BigDecimal("50.00"), report.otdPercentage());
        assertEquals(new BigDecimal("60.00"), report.averageDelayMinutes()); // 120 / 2 = 60
    }

    @Test
    @DisplayName("Calculate Fuel MPG with correct MetricAvailability and no fake zero")
    void testFuelMetrics() {
        Expense fuelExpense = new Expense();
        fuelExpense.setStatus("APPROVED");
        fuelExpense.setCategory("FUEL");
        fuelExpense.setAmountAmount(new BigDecimal("350.00"));
        fuelExpense.setAmountCurrency("USD");
        fuelExpense.setQuantity(new BigDecimal("100"));
        fuelExpense.setQuantityUnit("gallons");

        when(expenseRepository.findApprovedExpenses(any(), isNull(), isNull()))
                .thenReturn(List.of(fuelExpense));

        Trip trip = new Trip();
        trip.setDispatchedAt(OffsetDateTime.now());
        trip.setTotalDistance(600.0);
        trip.setActualDistanceMiles(new BigDecimal("600.000"));
        when(tripRepository.findAll()).thenReturn(List.of(trip));

        FuelReport report = operatingCostCalculator.calculateFuelMetrics(null, null, "USD");

        assertNotNull(report);
        assertEquals(new BigDecimal("350.00"), report.totalFuelCost());
        assertEquals(new BigDecimal("100.00"), report.totalGallons());
        // 350 / 100 = 3.5000
        assertEquals(new BigDecimal("3.5000"), report.averageCostPerGallon());
        // MPG = 600 / 100 = 6.00
        assertNotNull(report.mpg());
        assertEquals(MetricAvailability.AVAILABLE, report.mpg().availability());
        assertEquals(new BigDecimal("6.00"), report.mpg().value());
    }
}
