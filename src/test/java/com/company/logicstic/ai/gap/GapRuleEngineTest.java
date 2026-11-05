package com.company.logicstic.ai.gap;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Load;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GapRuleEngineTest {

    private GapRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GapRuleEngine();
    }

    private Load createCleanLoad() {
        Load load = new Load();
        load.setOriginLocationLatitude(41.8781);
        load.setOriginLocationLongitude(-87.6298);
        load.setDestinationLocationLatitude(32.7767);
        load.setDestinationLocationLongitude(-96.7970);
        load.setOriginAddressCity("Chicago");
        load.setOriginAddressState("IL");
        load.setDestinationAddressCity("Dallas");
        load.setDestinationAddressState("TX");
        load.setRequestedPickupDate(OffsetDateTime.now().plusDays(1));
        load.setRequestedDeliveryDate(OffsetDateTime.now().plusDays(3));
        load.setDeliveryCostAmount(BigDecimal.valueOf(1500.00));
        load.setIsHazmat(false);
        load.setType("Dry Van");
        load.setDistance(925.0);
        Employee dispatcher = new Employee();
        dispatcher.setFirstName("Sarah");
        dispatcher.setLastName("Connor");
        load.setAssignedDispatcher(dispatcher);
        return load;
    }

    @Test
    @DisplayName("Clean load has no gap violations")
    void evaluate_cleanLoad_noViolations() {
        Load load = createCleanLoad();
        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertTrue(violations.isEmpty(), "Expected no violations for clean load but got: " + violations);
    }

    @Test
    @DisplayName("Missing origin coordinates triggers BLOCKING gap")
    void evaluate_missingOriginCoordinates_blocking() {
        Load load = createCleanLoad();
        load.setOriginLocationLatitude(null);

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertEquals(1, violations.size());
        assertEquals("MISSING_ORIGIN_COORDINATES", violations.get(0).ruleCode());
        assertEquals("BLOCKING", violations.get(0).severity());
    }

    @Test
    @DisplayName("Missing destination coordinates triggers BLOCKING gap")
    void evaluate_missingDestinationCoordinates_blocking() {
        Load load = createCleanLoad();
        load.setDestinationLocationLongitude(0.0);
        load.setDestinationLocationLatitude(0.0);

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertEquals(1, violations.size());
        assertEquals("MISSING_DESTINATION_COORDINATES", violations.get(0).ruleCode());
        assertEquals("BLOCKING", violations.get(0).severity());
    }

    @Test
    @DisplayName("Missing origin address city triggers BLOCKING gap")
    void evaluate_missingOriginAddress_blocking() {
        Load load = createCleanLoad();
        load.setOriginAddressCity("  ");

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertTrue(violations.stream().anyMatch(v -> "MISSING_ORIGIN_ADDRESS".equals(v.ruleCode())));
    }

    @Test
    @DisplayName("Invalid appointment window (pickup after delivery) triggers BLOCKING gap")
    void evaluate_invalidAppointmentWindow_blocking() {
        Load load = createCleanLoad();
        load.setRequestedPickupDate(OffsetDateTime.now().plusDays(5));
        load.setRequestedDeliveryDate(OffsetDateTime.now().plusDays(2));

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertTrue(violations.stream().anyMatch(v -> "INVALID_APPOINTMENT_WINDOW".equals(v.ruleCode()) && "BLOCKING".equals(v.severity())));
    }

    @Test
    @DisplayName("Missing revenue triggers BLOCKING gap")
    void evaluate_missingRevenue_blocking() {
        Load load = createCleanLoad();
        load.setDeliveryCostAmount(BigDecimal.ZERO);

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertTrue(violations.stream().anyMatch(v -> "MISSING_REVENUE".equals(v.ruleCode()) && "BLOCKING".equals(v.severity())));
    }

    @Test
    @DisplayName("Missing hazmat details when isHazmat is true triggers BLOCKING gap")
    void evaluate_missingHazmatDetails_blocking() {
        Load load = createCleanLoad();
        load.setIsHazmat(true);
        load.setHazmatClass(null);
        load.setUnNumber("");

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertTrue(violations.stream().anyMatch(v -> "HAZMAT_DETAILS_MISSING".equals(v.ruleCode()) && "BLOCKING".equals(v.severity())));
    }

    @Test
    @DisplayName("Note conflict with load type triggers WARNING gap with proposed value")
    void evaluate_noteConflict_warning() {
        Load load = createCleanLoad();
        load.setType("Flatbed");
        load.setNotes("Lưu ý: Hàng thực phẩm cần xe đông lạnh giữ nhiệt độ -18C");

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        var conflict = violations.stream().filter(v -> "NOTE_CONFLICT".equals(v.ruleCode())).findFirst();
        assertTrue(conflict.isPresent());
        assertEquals("WARNING", conflict.get().severity());
        assertEquals("REEFER", conflict.get().proposedValue());
    }

    @Test
    @DisplayName("Missing dispatcher triggers WARNING gap")
    void evaluate_missingDispatcher_warning() {
        Load load = createCleanLoad();
        load.setAssignedDispatcher(null);

        List<GapRuleEngine.GapViolation> violations = engine.evaluate(load);
        assertTrue(violations.stream().anyMatch(v -> "MISSING_DISPATCHER".equals(v.ruleCode()) && "WARNING".equals(v.severity())));
    }
}
