package com.company.logicstic.service.calculation;

import com.company.logicstic.dto.report.DriverRankingItem;
import com.company.logicstic.dto.report.DriverRankingReport;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverRankingServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private TripRepository tripRepository;
    @Mock private TripDriverAssignmentRepository tripDriverAssignmentRepository;
    @Mock private LoadRepository loadRepository;
    @Mock private InvoiceRepository invoiceRepository;

    private DriverRankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new DriverRankingService(
                employeeRepository,
                tripRepository,
                tripDriverAssignmentRepository,
                loadRepository,
                invoiceRepository,
                ZoneOffset.UTC
        );
    }

    @Test
    @DisplayName("Returns empty drivers list when no drivers exist")
    void testGetRankingNoDrivers() {
        when(employeeRepository.findAll()).thenReturn(List.of());

        DriverRankingReport report = rankingService.getRanking(
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 10, 16),
                "USD",
                "score",
                10
        );

        assertNotNull(report);
        assertTrue(report.drivers().isEmpty());
        assertEquals("USD", report.currency());
        assertEquals("score", report.sortBy());
        assertFalse(report.isMock());
    }

    @Test
    @DisplayName("Calculates score, ranks and limits correctly for multiple drivers")
    void testGetRankingCalculationAndSorting() {
        Employee driver1 = new Employee();
        driver1.setId(UUID.randomUUID());
        driver1.setFirstName("Alice");
        driver1.setLastName("Smith");

        Employee driver2 = new Employee();
        driver2.setId(UUID.randomUUID());
        driver2.setFirstName("Bob");
        driver2.setLastName("Jones");

        when(employeeRepository.findAll()).thenReturn(List.of(driver1, driver2));

        // Trips and assignments
        Trip trip1 = new Trip();
        trip1.setActualDistanceMiles(new BigDecimal("1000.0"));
        trip1.setCompletedAt(OffsetDateTime.of(2026, 9, 20, 10, 0, 0, 0, ZoneOffset.UTC));

        TripDriverAssignment assign1 = new TripDriverAssignment();
        assign1.setDriver(driver1);
        assign1.setTrip(trip1);

        Trip trip2 = new Trip();
        trip2.setActualDistanceMiles(new BigDecimal("500.0"));
        trip2.setCompletedAt(OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC));

        TripDriverAssignment assign2 = new TripDriverAssignment();
        assign2.setDriver(driver2);
        assign2.setTrip(trip2);

        when(tripDriverAssignmentRepository.findAll()).thenReturn(List.of(assign1, assign2));

        // Trucks & Loads
        Truck truck1 = new Truck();
        truck1.setId(UUID.randomUUID());
        truck1.setMainDriver(driver1);

        Truck truck2 = new Truck();
        truck2.setId(UUID.randomUUID());
        truck2.setMainDriver(driver2);

        // Load 1 for Driver 1 (delivered on-time)
        Load load1 = new Load();
        load1.setId(UUID.randomUUID());
        load1.setAssignedTruck(truck1);
        load1.setDeliveredAt(OffsetDateTime.of(2026, 9, 20, 12, 0, 0, 0, ZoneOffset.UTC));
        load1.setRequestedDeliveryDate(OffsetDateTime.of(2026, 9, 20, 14, 0, 0, 0, ZoneOffset.UTC));

        // Load 2 for Driver 2 (delivered late, with 1 exception)
        Load load2 = new Load();
        load2.setId(UUID.randomUUID());
        load2.setAssignedTruck(truck2);
        load2.setDeliveredAt(OffsetDateTime.of(2026, 9, 22, 16, 0, 0, 0, ZoneOffset.UTC));
        load2.setRequestedDeliveryDate(OffsetDateTime.of(2026, 9, 22, 12, 0, 0, 0, ZoneOffset.UTC));
        LoadException exc = new LoadException();
        load2.setExceptions(List.of(exc));

        when(loadRepository.findAll()).thenReturn(List.of(load1, load2));

        // Invoices
        Invoice inv1 = new Invoice();
        inv1.setSubtotalAmount(new BigDecimal("2000.00"));
        inv1.setType("STANDARD");
        when(invoiceRepository.findAllByLoadId(load1.getId())).thenReturn(List.of(inv1));

        Invoice inv2 = new Invoice();
        inv2.setSubtotalAmount(new BigDecimal("800.00"));
        inv2.setType("STANDARD");
        when(invoiceRepository.findAllByLoadId(load2.getId())).thenReturn(List.of(inv2));

        // Execute ranking
        DriverRankingReport report = rankingService.getRanking(
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 10, 16),
                "USD",
                "score",
                10
        );

        assertNotNull(report);
        assertEquals(2, report.drivers().size());

        DriverRankingItem topDriver = report.drivers().get(0);
        assertEquals(driver1.getId().toString(), topDriver.driverId());
        assertEquals("Alice Smith", topDriver.driverName());
        assertEquals(1, topDriver.rank());
        assertEquals(1, topDriver.completedLoads());
        assertEquals(new BigDecimal("100.0"), topDriver.onTimePercent());
        assertEquals(new BigDecimal("1000.0"), topDriver.totalMiles());
        assertEquals(new BigDecimal("2000.00"), topDriver.revenue());
        assertEquals(0, topDriver.exceptionsCount());
        assertFalse(topDriver.isMock());

        DriverRankingItem secondDriver = report.drivers().get(1);
        assertEquals(driver2.getId().toString(), secondDriver.driverId());
        assertEquals("Bob Jones", secondDriver.driverName());
        assertEquals(2, secondDriver.rank());
        assertEquals(1, secondDriver.completedLoads());
        assertEquals(new BigDecimal("0.0"), secondDriver.onTimePercent());
        assertEquals(1, secondDriver.exceptionsCount());

        assertTrue(topDriver.score().compareTo(secondDriver.score()) > 0);

        // Test limit=1
        DriverRankingReport limited = rankingService.getRanking(
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 10, 16),
                "USD",
                "score",
                1
        );
        assertEquals(1, limited.drivers().size());
        assertEquals(driver1.getId().toString(), limited.drivers().get(0).driverId());
    }
}
