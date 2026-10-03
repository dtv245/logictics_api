package com.company.logicstic.service.profitability;

import com.company.logicstic.common.enums.ShipmentCostCategory;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class DefaultCostClassificationPolicyV1 implements CostClassificationPolicy {
    public String name() { return "LOGISTICSX_COST_CLASSIFICATION"; }
    public String version() { return "1"; }

    public Classification classify(Input input) {
        if (input == null) return unknown("MISSING_COST_INPUT");
        String basis = normalize(input.costBasis());
        if ("ESTIMATE".equals(basis) || "ACCRUAL".equals(basis))
            return new Classification(CostBehavior.EXCLUDED, "NOT_ACTUAL_COST");
        if (!"ACTUAL".equals(basis)) return unknown("UNKNOWN_COST_BASIS");
        ShipmentCostCategory category;
        try { category = ShipmentCostCategory.valueOf(normalize(input.category())); }
        catch (IllegalArgumentException ex) { return unknown("UNKNOWN_COST_CATEGORY"); }
        String source = normalize(input.sourceType());
        String method = normalize(input.allocationMethod());
        return switch (category) {
            case FUEL, DRIVER, TOLL, ACCESSORIAL, PERMIT ->
                    new Classification(CostBehavior.VARIABLE, "V1_VARIABLE_CATEGORY");
            case MAINTENANCE -> {
                if ("DIRECT".equals(method) && ("MANUAL".equals(source) || "EXPENSE".equals(source)
                        || "MAINTENANCE_RECORD".equals(source)))
                    yield new Classification(CostBehavior.VARIABLE, "EXPLICIT_DIRECT_MAINTENANCE");
                if ("ALLOCATION".equals(source) && "CPM_MILEAGE".equals(method))
                    yield new Classification(CostBehavior.FIXED_ALLOCATABLE, "EXPLICIT_MAINTENANCE_ALLOCATION");
                yield unknown("MAINTENANCE_REQUIRES_DIRECT_OR_CPM_ALLOCATION");
            }
            case INSURANCE -> new Classification(CostBehavior.FIXED_ALLOCATABLE, "V1_INSURANCE_FIXED_CATEGORY");
            case OTHER -> unknown("OTHER_HAS_NO_COST_BEHAVIOR_SEMANTICS");
        };
    }

    private Classification unknown(String reason) { return new Classification(CostBehavior.UNCLASSIFIED, reason); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
}
