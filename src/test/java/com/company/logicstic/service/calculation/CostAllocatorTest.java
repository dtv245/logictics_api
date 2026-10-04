package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.cost.CostAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CostAllocatorTest {
    final LoadRepository loads = mock(LoadRepository.class);
    final ShipmentCostRepository costs = mock(ShipmentCostRepository.class);
    final TripStopRepository stops = mock(TripStopRepository.class);
    final com.company.logicstic.service.cost.ShipmentCostEngine views = mock(com.company.logicstic.service.cost.ShipmentCostEngine.class);
    final CalculationSnapshotService snapshots = mock(CalculationSnapshotService.class);
    final CostAllocator allocator = new CostAllocator(loads, costs, views, stops, TestRoundingPolicies.standard(), snapshots);
    Load load; Trip trip; TripStop stop;

    @BeforeEach void setup() {
        load = new Load(); load.setId(UUID.randomUUID()); load.setDistance(99999d);
        Truck mutableTruck = new Truck(); mutableTruck.setId(UUID.randomUUID()); load.setAssignedTruck(mutableTruck);
        Truck actualTruck = new Truck(); actualTruck.setId(UUID.randomUUID());
        trip = new Trip(); trip.setId(UUID.randomUUID()); trip.setTruck(actualTruck); trip.setActualDistanceMiles(new BigDecimal("12.5"));
        stop = new TripStop(); stop.setTrip(trip); stop.setLoad(load);
        when(loads.findByIdForUpdate(load.getId())).thenReturn(Optional.of(load));
        when(stops.findByLoadId(load.getId())).thenReturn(List.of(stop));
        when(stops.findByTripIdOrderByOrderAsc(trip.getId())).thenReturn(List.of(stop));
    }
    @Test void persistsEstimateSnapshotAndHistoricalTruckNotMutableAssignment() {
        when(costs.saveAndFlush(any())).thenAnswer(invocation -> {
            ShipmentCost cost = invocation.getArgument(0); cost.setId(UUID.randomUUID()); return cost;
        });
        when(costs.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshots.recordSnapshot(anyString(), any(), anyString(), anyString(), anyString(), anyString(),
                isNull(), anyString(), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(new CalculationSnapshot());
        allocator.allocateMaintenanceCost(load.getId(), new BigDecimal("2.5"), "USD");
        var capture = org.mockito.ArgumentCaptor.forClass(ShipmentCost.class);
        verify(costs).save(capture.capture()); ShipmentCost cost = capture.getValue();
        assertEquals(new BigDecimal("31.25"), cost.getAmount()); assertEquals("ESTIMATE", cost.getCostBasis());
        assertSame(trip.getTruck(), cost.getTruck()); assertNotNull(cost.getCalculationSnapshot());
    }
    @Test void refusesSharedTripMileage() {
        TripStop other = new TripStop(); Load otherLoad = new Load(); otherLoad.setId(UUID.randomUUID()); other.setLoad(otherLoad);
        when(stops.findByTripIdOrderByOrderAsc(trip.getId())).thenReturn(List.of(stop, other));
        assertThrows(BadRequestException.class, () -> allocator.allocateMaintenanceCost(load.getId(), BigDecimal.ONE, "USD"));
        verifyNoInteractions(costs, snapshots);
    }
    @Test void refusesMissingExplicitMileageEvenWithLegacyDistance() {
        trip.setActualDistanceMiles(null);
        assertThrows(BadRequestException.class, () -> allocator.allocateMaintenanceCost(load.getId(), BigDecimal.ONE, "USD"));
        verifyNoInteractions(costs, snapshots);
    }
}
