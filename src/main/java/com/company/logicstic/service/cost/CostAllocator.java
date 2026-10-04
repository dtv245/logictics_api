package com.company.logicstic.service.cost;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.common.enums.CostBasis;
import com.company.logicstic.common.enums.CostSourceType;
import com.company.logicstic.common.enums.ShipmentCostCategory;
import com.company.logicstic.common.enums.ShipmentCostStatus;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.TripStop;
import com.company.logicstic.dto.cost.ShipmentCostView;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.calculation.CalculationSnapshotService;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.ShipmentCostRepository;
import com.company.logicstic.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostAllocator {

    private final LoadRepository loadRepository;
    private final ShipmentCostRepository shipmentCostRepository;
    private final ShipmentCostEngine shipmentCostEngine;
    private final TripStopRepository tripStopRepository;
    private final FinancialRoundingPolicy rounding;
    private final CalculationSnapshotService snapshots;

    @Transactional
    public ShipmentCostView allocateMaintenanceCost(UUID loadId, BigDecimal truckMaintenanceCpm, String currency) {
        Load load = loadRepository.findByIdForUpdate(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        String safeCurrency = CurrencyGuard.canonical(currency);
        if (truckMaintenanceCpm == null || truckMaintenanceCpm.signum() < 0) {
            throw new BadRequestException("truckMaintenanceCpm must be provided and non-negative");
        }
        if (truckMaintenanceCpm.stripTrailingZeros().scale() > 6) {
            throw new BadRequestException("Maintenance CPM exceeds the ledger's six-decimal rate precision");
        }
        MileageInputs inputs = resolveExplicitActualMiles(loadId);
        BigDecimal eligibleMiles = inputs.miles();

        // AllocatedMaintenance = LoadEligibleMiles * TruckMaintenanceCPM
        BigDecimal rawAmount = eligibleMiles.multiply(truckMaintenanceCpm);
        BigDecimal allocatedAmount = rounding.money(rawAmount, safeCurrency, FinancialRoundingPolicy.Boundary.ALLOCATION);

        // Invariant 6: Idempotent allocation
        var existingOpt = shipmentCostRepository.findBySourceTypeAndSourceId(CostSourceType.ALLOCATION.name(), loadId);
        if (existingOpt.isPresent()) {
            ShipmentCost existing = existingOpt.get();
            boolean unchanged = existing.getQuantity().compareTo(eligibleMiles) == 0
                    && existing.getUnitRate().compareTo(truckMaintenanceCpm) == 0
                    && existing.getAmount().compareTo(allocatedAmount) == 0
                    && safeCurrency.equals(existing.getCurrency())
                    && existing.getTruck() != null && existing.getTruck().getId().equals(inputs.truck().getId())
                    && existing.getCalculationSnapshot() != null
                    && sameSnapshotInput(existing, inputs, truckMaintenanceCpm);
            if (unchanged && existing.getCalculationSnapshot() != null) return shipmentCostEngine.toView(existing);
            if (ShipmentCostStatus.APPROVED.name().equalsIgnoreCase(existing.getStatus())
                    || ShipmentCostStatus.POSTED.name().equalsIgnoreCase(existing.getStatus())
                    || ShipmentCostStatus.VOIDED.name().equalsIgnoreCase(existing.getStatus())) {
                throw new BadRequestException("FINALIZED_SHIPMENT_COST_IMMUTABLE", "Finalized maintenance allocation cannot be changed");
            }
            existing.setTruck(inputs.truck());
            existing.setCostBasis(CostBasis.ESTIMATE.name());
            existing.setQuantity(eligibleMiles);
            existing.setUnitRate(truckMaintenanceCpm);
            existing.setAmount(allocatedAmount);
            existing.setCurrency(safeCurrency);
            recordSnapshot(existing, inputs, truckMaintenanceCpm);
            return shipmentCostEngine.toView(shipmentCostRepository.save(existing));
        }

        ShipmentCost cost = new ShipmentCost();
        cost.setLoad(load);
        cost.setTruck(inputs.truck());
        cost.setCategory(ShipmentCostCategory.MAINTENANCE.name());
        // A caller-supplied CPM is an estimate, not proof of an incurred actual cost.
        cost.setCostBasis(CostBasis.ESTIMATE.name());
        cost.setStatus(ShipmentCostStatus.VERIFIED.name());
        cost.setSourceType(CostSourceType.ALLOCATION.name());
        cost.setSourceId(loadId);
        cost.setAllocationMethod("CPM_MILEAGE");
        cost.setQuantity(eligibleMiles);
        cost.setUnit("MILES");
        cost.setUnitRate(truckMaintenanceCpm);
        cost.setAmount(allocatedAmount);
        cost.setCurrency(safeCurrency);
        cost.setIncurredAt(OffsetDateTime.now());
        cost.setNote("Maintenance allocation: " + eligibleMiles + " miles @ " + truckMaintenanceCpm + "/mi");

        ShipmentCost saved = shipmentCostRepository.saveAndFlush(cost);
        recordSnapshot(saved, inputs, truckMaintenanceCpm);
        return shipmentCostEngine.toView(shipmentCostRepository.save(saved));
    }

    private void recordSnapshot(ShipmentCost cost, MileageInputs inputs, BigDecimal rate) {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        String input = snapshotInput(cost, inputs, rate);
        String result = json.writeValueAsString(java.util.Map.of("amount", cost.getAmount(), "costBasis", cost.getCostBasis()));
        cost.setCalculationSnapshot(snapshots.recordSnapshot("SHIPMENT_COST", cost.getId(), "MAINTENANCE_ALLOCATION",
                "CostAllocator", "1.0", "FINANCIAL_ROUNDING", null, rounding.version(), input, result,
                cost.getCurrency(), null, null));
    }

    private String snapshotInput(ShipmentCost cost, MileageInputs inputs, BigDecimal rate) {
        // Stable key/trip ordering and decimal representation make retry detection deterministic.
        var values = new java.util.TreeMap<String, Object>();
        values.put("tripIds", inputs.tripIds().stream().sorted().toList());
        values.put("truckId", inputs.truck().getId());
        values.put("actualMiles", inputs.miles().stripTrailingZeros().toPlainString());
        values.put("maintenanceCpm", rate.stripTrailingZeros().toPlainString());
        values.put("currency", cost.getCurrency());
        values.put("roundingPolicyVersion", rounding.version());
        values.put("roundingMode", rounding.mode(FinancialRoundingPolicy.Boundary.ALLOCATION));
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(values);
    }

    private boolean sameSnapshotInput(ShipmentCost cost, MileageInputs inputs, BigDecimal rate) {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        return json.readTree(snapshotInput(cost, inputs, rate)).equals(json.readTree(cost.getCalculationSnapshot().getInputJson()));
    }

    private record MileageInputs(BigDecimal miles, Truck truck, java.util.List<UUID> tripIds) {}

    private MileageInputs resolveExplicitActualMiles(UUID loadId) {
        java.util.Map<UUID, Trip> trips = new java.util.LinkedHashMap<>();
        for (TripStop stop : tripStopRepository.findByLoadId(loadId)) {
            if (stop.getTrip() != null) trips.put(stop.getTrip().getId(), stop.getTrip());
        }
        if (trips.isEmpty()) throw new BadRequestException("No explicit trip mileage is attributable to this load");

        BigDecimal miles = BigDecimal.ZERO;
        Truck truck = null;
        for (Trip trip : trips.values()) {
            long distinctLoads = tripStopRepository.findByTripIdOrderByOrderAsc(trip.getId()).stream()
                    .map(TripStop::getLoad).filter(java.util.Objects::nonNull)
                    .map(load -> load.getId()).distinct().count();
            if (distinctLoads != 1) throw new BadRequestException("Trip mileage cannot be allocated across multiple loads without an allocation policy");
            if (trip.getTruck() == null || (truck != null && !truck.getId().equals(trip.getTruck().getId()))) {
                throw new BadRequestException("One historically attributed truck is required for a truck-specific CPM");
            }
            truck = trip.getTruck();
            if (trip.getActualDistanceMiles() == null || trip.getActualDistanceMiles().signum() <= 0) {
                throw new BadRequestException("Explicit actual trip miles are unavailable for load allocation");
            }
            miles = miles.add(trip.getActualDistanceMiles());
        }
        return new MileageInputs(miles, truck, java.util.List.copyOf(trips.keySet()));
    }
}
