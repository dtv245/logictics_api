package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.util.Optional;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.ShipmentCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Projects a single approved, attributed expense exactly once into the canonical ledger. */
@Service("approvedExpenseProjector") @RequiredArgsConstructor
public class ShipmentCostEngine {
    private final ShipmentCostRepository shipmentCostRepository;
    private final FinancialRoundingPolicy rounding;

    @Transactional
    public ShipmentCost projectApprovedExpense(Expense expense) {
        if (expense.getId() == null || expense.getLoad() == null) {
            throw new BadRequestException("SHIPMENT_COST_SOURCE_INVALID", "An attributed expense with an id and load is required");
        }
        if (!"APPROVED".equalsIgnoreCase(expense.getStatus())) {
            throw new BadRequestException("SHIPMENT_COST_SOURCE_NOT_APPROVED", "Only approved expenses may be projected");
        }
        Optional<ShipmentCost> existing = shipmentCostRepository.findBySourceTypeAndSourceId("EXPENSE", expense.getId());
        if (existing.isPresent()) {
            ShipmentCost projected = existing.get();
            boolean matches = projected.getAmount().compareTo(rounding.money(requireAmount(expense), expense.getAmountCurrency(), FinancialRoundingPolicy.Boundary.INVOICE)) == 0
                    && projected.getCurrency().equals(CurrencyGuard.normalize(expense.getAmountCurrency()))
                    && projected.getCategory().equals(mapCategory(expense.getCategory()))
                    && java.util.Objects.equals(projected.getLoad().getId(), expense.getLoad().getId())
                    && java.util.Objects.equals(projected.getTrip() == null ? null : projected.getTrip().getId(), expense.getTrip() == null ? null : expense.getTrip().getId())
                    && java.util.Objects.equals(projected.getTruck() == null ? null : projected.getTruck().getId(), expense.getTruck() == null ? null : expense.getTruck().getId())
                    && java.util.Objects.equals(projected.getDriver() == null ? null : projected.getDriver().getId(), expense.getEmployee() == null ? null : expense.getEmployee().getId())
                    && java.util.Objects.equals(projected.getIncurredAt(), expense.getExpenseDate());
            if ("APPROVED".equalsIgnoreCase(projected.getStatus()) || "POSTED".equalsIgnoreCase(projected.getStatus())
                    || "VOIDED".equalsIgnoreCase(projected.getStatus())) {
                if (!matches) throw new BadRequestException("FINALIZED_SHIPMENT_COST_IMMUTABLE", "Finalized cost differs from its source expense");
                return projected;
            }
            projected.setLoad(expense.getLoad());
            projected.setTrip(expense.getTrip());
            projected.setTruck(expense.getTruck());
            projected.setDriver(expense.getEmployee());
            String category = mapCategory(expense.getCategory());
            projected.setCategory(category);
            projected.setAllocationMethod(allocationMethodFor(category));
            projected.setAmount(rounding.money(requireAmount(expense), expense.getAmountCurrency(), FinancialRoundingPolicy.Boundary.INVOICE));
            projected.setCurrency(CurrencyGuard.normalize(expense.getAmountCurrency()));
            projected.setIncurredAt(expense.getExpenseDate());
            return shipmentCostRepository.save(projected);
        }

        ShipmentCost cost = new ShipmentCost();
        cost.setLoad(expense.getLoad());
        cost.setTrip(expense.getTrip());
        cost.setTruck(expense.getTruck());
        cost.setDriver(expense.getEmployee());
        String category = mapCategory(expense.getCategory());
        cost.setCategory(category);
        // A direct expense is the evidence a maintenance cost was actually incurred; without
        // DIRECT the V1 policy cannot classify it and it would silently leave profitability.
        cost.setAllocationMethod(allocationMethodFor(category));
        cost.setCostBasis("ACTUAL");
        cost.setStatus("VERIFIED");
        cost.setSourceType("EXPENSE");
        cost.setSourceId(expense.getId());
        cost.setAmount(rounding.money(requireAmount(expense), expense.getAmountCurrency(), FinancialRoundingPolicy.Boundary.INVOICE));
        cost.setCurrency(CurrencyGuard.normalize(expense.getAmountCurrency()));
        cost.setIncurredAt(expense.getExpenseDate());
        return shipmentCostRepository.save(cost);
    }

    public BigDecimal allocateMaintenance(BigDecimal loadEligibleMiles, BigDecimal truckMaintenanceCpm, String currency) {
        if (loadEligibleMiles == null || truckMaintenanceCpm == null || loadEligibleMiles.signum() < 0 || truckMaintenanceCpm.signum() < 0) {
            throw new BadRequestException("COST_ALLOCATION_INPUT_INVALID", "Non-negative eligible miles and maintenance CPM are required");
        }
        return rounding.money(loadEligibleMiles.multiply(truckMaintenanceCpm), currency, FinancialRoundingPolicy.Boundary.ALLOCATION);
    }

    private BigDecimal requireAmount(Expense expense) {
        if (expense.getAmountAmount() == null || expense.getAmountAmount().signum() < 0 || expense.getAmountCurrency() == null) {
            throw new BadRequestException("SHIPMENT_COST_SOURCE_INVALID", "Expense amount and currency are required");
        }
        return expense.getAmountAmount();
    }

    /**
     * The expense vocabulary (FUEL, TOLL, PARKING, SCALE, REPAIR, MAINTENANCE, TIRE, LUMPER,
     * HOTEL, PERMIT, OTHER) is coarser than the ledger vocabulary. Every mapping here must land
     * on a category the V1 profitability policy can classify; OTHER intentionally cannot.
     */
    private String mapCategory(String category) {
        if (category == null) return "OTHER";
        String value = category.trim().toUpperCase();
        if (value.contains("FUEL")) return "FUEL";
        if (value.contains("TOLL") || value.contains("PARKING") || value.contains("SCALE")) return "TOLL";
        if (value.contains("MAINT") || value.contains("REPAIR") || value.contains("TIRE")) return "MAINTENANCE";
        if (value.contains("DRIVER") || value.contains("PAYROLL") || value.contains("HOTEL")) return "DRIVER";
        if (value.contains("ACCESS") || value.contains("LUMPER")) return "ACCESSORIAL";
        if (value.contains("INSUR")) return "INSURANCE";
        if (value.contains("PERMIT")) return "PERMIT";
        return "OTHER";
    }

    private String allocationMethodFor(String category) {
        return "MAINTENANCE".equals(category) ? "DIRECT" : null;
    }
}
