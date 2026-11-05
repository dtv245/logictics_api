package com.company.logicstic.service.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.dto.cost.CreateShipmentCostRequest;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.TripStop;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.ShipmentCostRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import com.company.logicstic.repository.TruckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShipmentCostWorkflowTest {

    @Mock ShipmentCostRepository costs;
    @Mock LoadRepository loads;
    @Mock TripRepository trips;
    @Mock TripStopRepository stops;
    @Mock TruckRepository trucks;
    @Mock ExpenseRepository expenses;
    @Mock EmployeeRepository employees;
    @Mock com.company.logicstic.service.calculation.ShipmentCostEngine projector;

    ShipmentCostEngine engine;
    UUID loadId;
    Load load;

    @BeforeEach
    void setup() {
        engine = new ShipmentCostEngine(costs, loads, trips, stops, trucks, expenses, employees, projector);
        loadId = UUID.randomUUID();
        load = new Load();
        load.setId(loadId);
    }

    @Test
    void manualCostRejectsTripThatIsNotOnTheLoad() {
        Trip trip = trip(UUID.randomUUID());
        when(loads.findById(loadId)).thenReturn(Optional.of(load));
        when(trips.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(stops.findByLoadId(loadId)).thenReturn(List.of());

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> engine.createCost(loadId, request(trip.getId(), null)));

        assertEquals("SHIPMENT_COST_ATTRIBUTION_INVALID", failure.getCode());
    }

    @Test
    void manualCostRejectsTruckThatDiffersFromTheTripTruck() {
        Trip trip = trip(UUID.randomUUID());
        TripStop stop = new TripStop();
        stop.setTrip(trip);
        Truck truck = new Truck();
        truck.setId(UUID.randomUUID());
        when(loads.findById(loadId)).thenReturn(Optional.of(load));
        when(trips.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(stops.findByLoadId(loadId)).thenReturn(List.of(stop));
        when(trucks.findById(truck.getId())).thenReturn(Optional.of(truck));

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> engine.createCost(loadId, request(trip.getId(), truck.getId())));

        assertEquals("SHIPMENT_COST_ATTRIBUTION_INVALID", failure.getCode());
    }

    @Test
    void verifyIsIdempotentAndKeepsTheOriginalAudit() {
        ShipmentCost cost = cost("DRAFT");
        when(costs.findByIdForUpdate(cost.getId())).thenReturn(Optional.of(cost));
        when(costs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID firstActor = UUID.randomUUID();

        var verified = engine.verifyCost(loadId, cost.getId(), firstActor);

        assertEquals("VERIFIED", verified.status());
        assertEquals(firstActor, verified.verifiedBy());
        OffsetDateTime firstTimestamp = verified.verifiedAt();

        var retried = engine.verifyCost(loadId, cost.getId(), UUID.randomUUID());

        assertEquals("VERIFIED", retried.status());
        assertEquals(firstActor, retried.verifiedBy());
        assertEquals(firstTimestamp, retried.verifiedAt());
    }

    @Test
    void approvalRequiresVerificationFirst() {
        ShipmentCost draft = cost("DRAFT");
        when(costs.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> engine.approveCost(loadId, draft.getId(), UUID.randomUUID()));

        assertEquals("SHIPMENT_COST_STATE_INVALID", failure.getCode());
    }

    @Test
    void approvalRecordsTheActorAndIsIdempotent() {
        ShipmentCost verified = cost("VERIFIED");
        when(costs.findByIdForUpdate(verified.getId())).thenReturn(Optional.of(verified));
        when(costs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID actor = UUID.randomUUID();

        var approved = engine.approveCost(loadId, verified.getId(), actor);

        assertEquals("APPROVED", approved.status());
        assertEquals(actor, approved.approvedBy());
        assertEquals("APPROVED", engine.approveCost(loadId, verified.getId(), UUID.randomUUID()).status());
    }

    @Test
    void voidRequiresAReasonAndRecordsTheAudit() {
        ShipmentCost approved = cost("APPROVED");
        when(costs.findByIdForUpdate(approved.getId())).thenReturn(Optional.of(approved));
        when(costs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(BadRequestException.class, () -> engine.voidCost(loadId, approved.getId(), "  ", UUID.randomUUID()));

        UUID actor = UUID.randomUUID();
        var voided = engine.voidCost(loadId, approved.getId(), "Duplicate invoice", actor);

        assertEquals("VOIDED", voided.status());
        assertEquals(actor, voided.voidedBy());
        assertEquals("Duplicate invoice", voided.voidReason());
    }

    @Test
    void costFromAnotherLoadIsNotAddressableThroughThisLoad() {
        ShipmentCost cost = new ShipmentCost();
        cost.setId(UUID.randomUUID());
        Load other = new Load();
        other.setId(UUID.randomUUID());
        cost.setLoad(other);
        when(costs.findByIdForUpdate(cost.getId())).thenReturn(Optional.of(cost));

        assertThrows(com.company.logicstic.exception.ResourceNotFoundException.class,
                () -> engine.verifyCost(loadId, cost.getId(), UUID.randomUUID()));
    }

    private ShipmentCost cost(String status) {
        ShipmentCost cost = new ShipmentCost();
        cost.setId(UUID.randomUUID());
        cost.setLoad(load);
        cost.setStatus(status);
        return cost;
    }

    private Trip trip(UUID truckId) {
        Trip trip = new Trip();
        trip.setId(UUID.randomUUID());
        if (truckId != null) {
            Truck truck = new Truck();
            truck.setId(truckId);
            trip.setTruck(truck);
        }
        return trip;
    }

    private CreateShipmentCostRequest request(UUID tripId, UUID truckId) {
        return new CreateShipmentCostRequest(tripId, truckId, null, "OTHER", "ACTUAL", null, "MANUAL",
                null, "DIRECT", null, null, null, new BigDecimal("10.00"), "USD", null, null);
    }
}
