package com.company.logicstic.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.dto.trip.AssignDriverRequest;
import com.company.logicstic.dto.trip.TripDriverAssignmentView;
import com.company.logicstic.dto.trip.TripStopView;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.TripDriverAssignment;
import com.company.logicstic.entity.TripStop;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripExecutionServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TripDriverAssignmentRepository assignmentRepository;
    @Mock
    private TripStopRepository tripStopRepository;

    @InjectMocks
    private TripExecutionService tripExecutionService;

    private UUID tripId;
    private UUID driverId;
    private Trip trip;
    private Employee driver;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        driverId = UUID.randomUUID();

        trip = new Trip();
        trip.setId(tripId);

        driver = new Employee();
        driver.setId(driverId);
        driver.setFirstName("John");
        driver.setLastName("Doe");
    }

    @Test
    @DisplayName("assignDriver successfully creates and activates trip driver assignment")
    void testAssignDriver() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(employeeRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(assignmentRepository.save(any(TripDriverAssignment.class))).thenAnswer(invocation -> {
            TripDriverAssignment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AssignDriverRequest req = new AssignDriverRequest(
                driverId,
                "PRIMARY",
                OffsetDateTime.now(),
                BigDecimal.valueOf(450.50)
        );

        TripDriverAssignmentView view = tripExecutionService.assignDriver(tripId, req);

        assertNotNull(view);
        assertEquals(tripId, view.tripId());
        assertEquals(driverId, view.driverId());
        assertEquals("John Doe", view.driverName());
        assertEquals("PRIMARY", view.assignmentType());
        assertEquals(BigDecimal.valueOf(450.50), view.plannedMiles());
        assertTrue(view.isActive());
        assertNull(view.effectiveTo());
    }

    @Test
    @DisplayName("unassignDriver soft-closes driver assignment without deleting history")
    void testUnassignDriverSoftClose() {
        UUID assignmentId = UUID.randomUUID();
        TripDriverAssignment assignment = new TripDriverAssignment();
        assignment.setId(assignmentId);
        assignment.setTrip(trip);
        assignment.setDriver(driver);
        assignment.setAssignmentType("PRIMARY");
        assignment.setEffectiveFrom(OffsetDateTime.now().minusDays(1));

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(TripDriverAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripDriverAssignmentView view = tripExecutionService.unassignDriver(tripId, assignmentId);

        assertNotNull(view);
        assertNotNull(view.effectiveTo(), "effectiveTo must be populated for soft-close");

        ArgumentCaptor<TripDriverAssignment> captor = ArgumentCaptor.forClass(TripDriverAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertNotNull(captor.getValue().getEffectiveTo());
    }

    @Test
    @DisplayName("unassignDriver throws BadRequestException if assignment belongs to different trip")
    void testUnassignDriverWrongTripThrows() {
        UUID assignmentId = UUID.randomUUID();
        Trip otherTrip = new Trip();
        otherTrip.setId(UUID.randomUUID());

        TripDriverAssignment assignment = new TripDriverAssignment();
        assignment.setId(assignmentId);
        assignment.setTrip(otherTrip);
        assignment.setDriver(driver);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(BadRequestException.class, () -> tripExecutionService.unassignDriver(tripId, assignmentId));
    }

    @Test
    @DisplayName("TripStop execution transitions arrive -> start-service -> complete-service -> depart with dwell time")
    void testTripStopLifecycle() {
        UUID stopId = UUID.randomUUID();
        TripStop stop = new TripStop();
        stop.setId(stopId);
        stop.setTrip(trip);
        stop.setOrder(1);
        stop.setType("DELIVERY");

        when(tripStopRepository.findById(stopId)).thenReturn(Optional.of(stop));
        when(tripStopRepository.save(any(TripStop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Arrive
        TripStopView arrivedView = tripExecutionService.arriveStop(stopId);
        assertEquals("ARRIVED", arrivedView.status());
        assertNotNull(arrivedView.arrivedAt());

        // 2. Start Service
        TripStopView startedView = tripExecutionService.startStopService(stopId);
        assertEquals("SERVICE_STARTED", startedView.status());
        assertNotNull(startedView.serviceStartedAt());

        // 3. Complete Service
        TripStopView completedView = tripExecutionService.completeStopService(stopId);
        assertEquals("SERVICE_COMPLETED", completedView.status());
        assertNotNull(completedView.serviceCompletedAt());

        // 4. Depart
        // Simulate arrived 45 minutes before departure
        stop.setArrivedAt(OffsetDateTime.now().minusMinutes(45));
        TripStopView departedView = tripExecutionService.departStop(stopId);
        assertEquals("DEPARTED", departedView.status());
        assertNotNull(departedView.departedAt());
        assertNotNull(departedView.dwellMinutes());
        assertTrue(departedView.dwellMinutes() >= 44 && departedView.dwellMinutes() <= 46);
    }

    @Test
    @DisplayName("TripStop rejects an invalid direct state transition")
    void departBeforeServiceCompletionIsRejected() {
        UUID stopId = UUID.randomUUID();
        TripStop stop = new TripStop();
        stop.setId(stopId);
        stop.setStatus("PENDING");
        when(tripStopRepository.findById(stopId)).thenReturn(Optional.of(stop));

        assertThrows(BadRequestException.class, () -> tripExecutionService.departStop(stopId));
    }
}
