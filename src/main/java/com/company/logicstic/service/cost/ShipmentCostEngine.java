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
import com.company.logicstic.entity.Truck;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.ShipmentCostRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentCostEngine {

    private final ShipmentCostRepository shipmentCostRepository;
    private final LoadRepository loadRepository;
    private final TripRepository tripRepository;
    private final TruckRepository truckRepository;
    private final ExpenseRepository expenseRepository;
    private final EmployeeRepository employeeRepository;
    private final com.company.logicstic.service.calculation.ShipmentCostEngine approvedExpenseProjector;

    @Transactional
    public ShipmentCostView createCost(UUID loadId, CreateShipmentCostRequest req) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        ShipmentCost cost = new ShipmentCost();
        cost.setLoad(load);
        if (req.tripId() != null) cost.setTrip(tripRepository.findById(req.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + req.tripId())));
        if (req.truckId() != null) cost.setTruck(truckRepository.findById(req.truckId())
                .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + req.truckId())));
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
    public List<ShipmentCostView> getCostsForLoad(UUID loadId) {
        if (!loadRepository.existsById(loadId)) {
            throw new ResourceNotFoundException("Load not found: " + loadId);
        }
        return shipmentCostRepository.findByLoadId(loadId).stream()
                .map(this::toView)
                .toList();
    }

    private String mapCategory(String expCat) {
        if (expCat == null) return ShipmentCostCategory.OTHER.name();
        String upper = expCat.toUpperCase();
        if (upper.contains("FUEL")) return ShipmentCostCategory.FUEL.name();
        if (upper.contains("TOLL")) return ShipmentCostCategory.TOLL.name();
        if (upper.contains("MAINT")) return ShipmentCostCategory.MAINTENANCE.name();
        if (upper.contains("DRIVER") || upper.contains("PAYROLL")) return ShipmentCostCategory.DRIVER.name();
        if (upper.contains("ACCESS")) return ShipmentCostCategory.ACCESSORIAL.name();
        if (upper.contains("INSUR")) return ShipmentCostCategory.INSURANCE.name();
        return ShipmentCostCategory.OTHER.name();
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
                c.getApprovedAt(),
                c.getPostedAt(),
                c.getNote(),
                c.getVersion()
        );
    }
}
