package com.company.logicstic.service.calculation;

import com.company.logicstic.common.enums.ShipmentCostCategory;
import com.company.logicstic.service.profitability.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CostClassificationPolicyTest {
    final CostClassificationPolicy policy = new DefaultCostClassificationPolicyV1();
    CostClassificationPolicy.Classification classify(String category, String source, String basis, String method) {
        return policy.classify(new CostClassificationPolicy.Input(category, source, basis, method));
    }
    @Test void everyExistingCategoryHasExplicitV1BehaviorIncludingUnknownSemantics() {
        assertEquals("LOGISTICSX_COST_CLASSIFICATION", policy.name()); assertEquals("1", policy.version());
        for (var category : ShipmentCostCategory.values()) {
            var result = classify(category.name(), "MANUAL", "ACTUAL", null);
            var expected = switch (category) {
                case FUEL, DRIVER, TOLL, ACCESSORIAL, PERMIT -> CostBehavior.VARIABLE;
                case INSURANCE -> CostBehavior.FIXED_ALLOCATABLE;
                case MAINTENANCE, OTHER -> CostBehavior.UNCLASSIFIED;
            };
            assertEquals(expected, result.behavior(), category.name()); assertNotNull(result.reason());
        }
    }
    @Test void maintenanceRequiresEvidenceAndConflictingMetadataIsNeverGuessed() {
        for (var source : new String[] {"MANUAL", "EXPENSE", "MAINTENANCE_RECORD"}) {
            assertEquals(CostBehavior.VARIABLE, classify("MAINTENANCE",source,"ACTUAL","DIRECT").behavior());
            assertEquals(CostBehavior.UNCLASSIFIED, classify("MAINTENANCE",source,"ACTUAL",null).behavior());
        }
        assertEquals(CostBehavior.FIXED_ALLOCATABLE, classify("MAINTENANCE","ALLOCATION","ACTUAL","CPM_MILEAGE").behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, classify("MAINTENANCE","ALLOCATION","ACTUAL","DIRECT").behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, classify("MAINTENANCE","MANUAL","ACTUAL","CPM_MILEAGE").behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, classify("MAINTENANCE","ALLOCATION","ACTUAL","UNKNOWN").behavior());
    }
    @Test void nonActualBasisIsExcludedAndInvalidInputRemainsUnknown() {
        assertEquals(CostBehavior.EXCLUDED, classify("MAINTENANCE","ALLOCATION","ESTIMATE","CPM_MILEAGE").behavior());
        assertEquals(CostBehavior.EXCLUDED, classify("FUEL","MANUAL","ACCRUAL",null).behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, classify("REPAIR","MANUAL","ACTUAL",null).behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, classify(null,null,"ACTUAL",null).behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, classify("FUEL",null,null,null).behavior());
        assertEquals(CostBehavior.UNCLASSIFIED, policy.classify(null).behavior());
        assertEquals(CostBehavior.VARIABLE, classify(" fuel ","manual","actual",null).behavior());
    }
}
