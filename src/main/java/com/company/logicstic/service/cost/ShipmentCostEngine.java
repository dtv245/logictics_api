package com.company.logicstic.service.cost;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.enums.CostBasis;
import com.company.logicstic.common.enums.CostSourceType;
import com.company.logicstic.common.enums.ShipmentCostCategory;
import com.company.logicstic.common.enums.ShipmentCostStatus;
import com.company.logicstic.dto.cost.CreateShipmentCostRequest;
import com.company.logicstic.dto.cost.ShipmentCostView;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.ShipmentCostRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import com.company.logicstic.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ShipmentCostEngine {

    private final ShipmentCostRepository shipmentCostRepository;
    private final LoadRepository loadRepository;
    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final TruckRepository truckRepository;
    private final ExpenseRepository expenseRepository;
    private final EmployeeRepository employeeRepository;
    private final com.company.logicstic.service.calculation.ShipmentCostEngine approvedExpenseProjector;

    @Transactional
    public ShipmentCostView createCost(UUID loadId, CreateShipmentCostRequest req) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        Trip trip = null;
        if (req.tripId() != null) {
            trip = tripRepository.findById(req.tripId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + req.tripId()));
            requireTripBelongsToLoad(loadId, trip);
        }
        Truck truck = null;
        if (req.truckId() != null) {
            truck = truckRepository.findById(req.truckId())
                    .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + req.truckId()));
        }
        if (trip != null && truck != null && trip.getTruck() != null
                && !truck.getId().equals(trip.getTruck().getId())) {
            throw new BadRequestException("SHIPMENT_COST_ATTRIBUTION_INVALID",
                    "Cost truck " + truck.getId() + " does not match trip truck " + trip.getTruck().getId());
        }

        ShipmentCost cost = new ShipmentCost();
        cost.setLoad(load);
        cost.setTrip(trip);
        cost.setTruck(truck);
        if (req.driverId() != null) cost.setDriver(employeeRepository.findById(req.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + req.driverId())));
        cost.setCategory(requireEnum(ShipmentCostCategory.class, req.category(), "category"));
        cost.setCostBasis(requireEnum(CostBasis.class, req.costBasis(), "costBasis"));
        String initialStatus = req.status() == null || req.status().isBlank()
                ? ShipmentCostStatus.DRAFT.name() : requireEnum(ShipmentCostStatus.class, req.status(), "status");
        if (!ShipmentCostStatus.DRAFT.name().equals(initialStatus) && !ShipmentCostStatus.VERIFIED.name().equals(initialStatus)) {
            throw new BadRequestException("New shipment costs must start in DRAFT or VERIFIED status");
        }
        cost.setStatus(initialStatus);
        cost.setSourceType(requireEnum(CostSourceType.class, req.sourceType(), "sourceType"));
        if (!CostSourceType.MANUAL.name().equals(cost.getSourceType())) {
            throw new BadRequestException("SHIPMENT_COST_SOURCE_RESERVED", "System source types must be created by their owning projection commands");
        }
        if (req.amount() == null || req.amount().signum() < 0) {
            throw new BadRequestException("New shipment costs must include a non-negative amount");
        }
        if (req.sourceId() != null && shipmentCostRepository.existsBySourceTypeAndSourceId(cost.getSourceType(), req.sourceId())) {
            throw new BadRequestException("Shipment cost already exists for this source");
        }
        cost.setSourceId(req.sourceId());
        cost.setAllocationMethod(req.allocationMethod());
        cost.setQuantity(req.quantity());
        cost.setUnit(req.unit());
        cost.setUnitRate(req.unitRate());
        cost.setAmount(req.amount());
        cost.setCurrency(CurrencyGuard.canonical(req.currency()));
        cost.setIncurredAt(req.incurredAt() != null ? req.incurredAt() : OffsetDateTime.now(java.time.ZoneOffset.UTC));
        cost.setNote(req.note());

        ShipmentCost saved = shipmentCostRepository.save(cost);
        return toView(saved);
    }

    /** DRAFT -> VERIFIED. Repeating the command on a VERIFIED cost is a no-op that preserves its audit. */
    @Transactional
    public ShipmentCostView verifyCost(UUID loadId, UUID costId, UUID actorId) {
        ShipmentCost cost = requireCostForUpdate(loadId, costId);
        if (ShipmentCostStatus.VERIFIED.name().equals(cost.getStatus())) {
            return toView(cost);
        }
        if (!ShipmentCostStatus.DRAFT.name().equals(cost.getStatus())) {
            throw new BadRequestException("SHIPMENT_COST_STATE_INVALID",
                    "Only DRAFT costs can be verified; current status is " + cost.getStatus());
        }
        cost.setStatus(ShipmentCostStatus.VERIFIED.name());
        cost.setVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        cost.setVerifiedBy(actorId);
        return toView(shipmentCostRepository.save(cost));
    }

    /** VERIFIED -> APPROVED. Approval is what makes an ACTUAL cost contribute to profitability. */
    @Transactional
    public ShipmentCostView approveCost(UUID loadId, UUID costId, UUID actorId) {
        ShipmentCost cost = requireCostForUpdate(loadId, costId);
        if (ShipmentCostStatus.APPROVED.name().equals(cost.getStatus())) {
            return toView(cost);
        }
        if (!ShipmentCostStatus.VERIFIED.name().equals(cost.getStatus())) {
            throw new BadRequestException("SHIPMENT_COST_STATE_INVALID",
                    "Only VERIFIED costs can be approved; current status is " + cost.getStatus());
        }
        cost.setStatus(ShipmentCostStatus.APPROVED.name());
        cost.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        cost.setApprovedBy(actorId);
        return toView(shipmentCostRepository.save(cost));
    }

    /** Any non-voided state -> VOIDED with a mandatory reason and an audit trail. */
    @Transactional
    public ShipmentCostView voidCost(UUID loadId, UUID costId, String reason, UUID actorId) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("SHIPMENT_COST_VOID_REASON_REQUIRED", "A void reason is required");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 500) {
            throw new BadRequestException("SHIPMENT_COST_VOID_REASON_INVALID", "Void reason must be at most 500 characters");
        }
        ShipmentCost cost = requireCostForUpdate(loadId, costId);
        if (ShipmentCostStatus.VOIDED.name().equals(cost.getStatus())) {
            return toView(cost);
        }
        cost.setStatus(ShipmentCostStatus.VOIDED.name());
        cost.setVoidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        cost.setVoidedBy(actorId);
        cost.setVoidReason(trimmedReason);
        return toView(shipmentCostRepository.save(cost));
    }

    private ShipmentCost requireCostForUpdate(UUID loadId, UUID costId) {
        ShipmentCost cost = shipmentCostRepository.findByIdForUpdate(costId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment cost not found: " + costId));
        if (cost.getLoad() == null || !loadId.equals(cost.getLoad().getId())) {
            throw new ResourceNotFoundException("Shipment cost not found: " + costId);
        }
        return cost;
    }

    private void requireTripBelongsToLoad(UUID loadId, Trip trip) {
        boolean attributed = tripStopRepository.findByLoadId(loadId).stream()
                .anyMatch(stop -> stop.getTrip() != null && trip.getId().equals(stop.getTrip().getId()));
        if (!attributed) {
            throw new BadRequestException("SHIPMENT_COST_ATTRIBUTION_INVALID",
                    "Trip " + trip.getId() + " is not assigned to load " + loadId);
        }
    }

    @Transactional
    public ShipmentCostView syncExpenseToShipmentCost(UUID expenseId) {
        Expense expense = expenseRepository.findByIdForUpdate(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));

        if (expense.getLoad() == null) {
            throw new BadRequestException("Expense " + expenseId + " has no load attached for shipment cost projection");
        }
        if (!"approved".equalsIgnoreCase(expense.getStatus())) {
            throw new BadRequestException("Only approved expenses can be projected to shipment costs");
        }

        return toView(approvedExpenseProjector.projectApprovedExpense(expense));
    }

    @Transactional
    public int syncAllExpensesForLoad(UUID loadId) {
        List<Expense> expenses = expenseRepository.findByLoadId(loadId);
        int synced = 0;
        for (Expense expense : expenses) {
            if ("approved".equalsIgnoreCase(expense.getStatus())) {
                syncExpenseToShipmentCost(expense.getId());
                synced++;
            }
        }
        return synced;
    }

    @Transactional(readOnly = true)
    public Page<ShipmentCostView> getCostsForLoad(UUID loadId, Pageable pageable) {
        if (!loadRepository.existsById(loadId)) {
            throw new ResourceNotFoundException("Load not found: " + loadId);
        }
        return shipmentCostRepository.findPageByLoadId(loadId, pageable).map(this::toView);
    }

    private <E extends Enum<E>> String requireEnum(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase()).name();
        } catch (RuntimeException ex) {
            throw new BadRequestException("Unsupported " + field + ": " + value);
        }
    }

    public ShipmentCostView toView(ShipmentCost c) {
        return new ShipmentCostView(
                c.getId(),
                c.getLoad() == null ? null : c.getLoad().getId(),
                c.getTrip() != null ? c.getTrip().getId() : null,
                c.getTruck() != null ? c.getTruck().getId() : null,
                c.getDriver() != null ? c.getDriver().getId() : null,
                c.getCategory(),
                c.getCostBasis(),
                c.getStatus(),
                c.getSourceType(),
                c.getSourceId(),
                c.getAllocationMethod(),
                c.getQuantity(),
                c.getUnit(),
                c.getUnitRate(),
                c.getAmount(),
                c.getCurrency(),
                c.getIncurredAt(),
                c.getVerifiedAt(),
                c.getVerifiedBy(),
                c.getApprovedAt(),
                c.getApprovedBy(),
                c.getPostedAt(),
                c.getVoidedAt(),
                c.getVoidedBy(),
                c.getVoidReason(),
                c.getNote(),
                c.getVersion()
        );
    }
}
