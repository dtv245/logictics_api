package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.payroll.MileagePayCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MileagePayCalculatorTest {
    final MileagePayCalculator calculator = new MileagePayCalculator(TestRoundingPolicies.standard());
    DriverPayPolicy policy(String basis) {
        var p = new DriverPayPolicy(); p.setId(UUID.randomUUID()); p.setPolicyVersion(3); p.setMileageBasis(basis);
        p.setPerMileRate(new BigDecimal("0.625")); p.setCurrency("USD"); return p;
    }
    TripDriverAssignment assignment() {
        var a = new TripDriverAssignment(); a.setId(UUID.randomUUID()); a.setActualMiles(new BigDecimal("123.456")); a.setPlannedMiles(new BigDecimal("200")); return a;
    }
    @Test void actualAndPlannedHaveDistinctExplicitInputsAndVersionedExplanation() {
        var a = assignment(); var p = policy("ACTUAL_ALL_MILES"); var r = calculator.calculate(a,p);
        assertEquals(new BigDecimal("77.160000"),r.rawAmount()); assertEquals(new BigDecimal("77.16"),r.amount());
        assertEquals(a.getId(),r.assignmentId()); assertEquals(p.getId(),r.policyId()); assertEquals(3,r.policyVersion());
        assertEquals("ACTUAL_ALL_MILES",r.mileageBasis()); assertNotNull(r.roundingPolicyVersion());
        assertEquals(new BigDecimal("125.00"),calculator.calculate(a,policy("PLANNED_ALL_MILES")).amount());
    }
    @Test void missingActualNeverFallsBackToPlannedOrTripMiles() {
        var a = assignment(); a.setActualMiles(null); var trip = new Trip(); trip.setActualDistanceMiles(new BigDecimal("999")); a.setTrip(trip);
        assertEquals("MILEAGE_VALIDATION_REQUIRED",assertThrows(BadRequestException.class, () -> calculator.calculate(a,policy("ACTUAL_ALL_MILES"))).getCode());
        assertEquals(new BigDecimal("125.00"),calculator.calculate(a,policy("PLANNED_ALL_MILES")).amount());
    }
    @Test void unavailableBasesAndNegativeOrMissingRatesFailClosed() {
        for (String basis : new String[]{"ACTUAL_LOADED_MILES","PLANNED_LOADED_MILES","PRACTICAL_MILES","CONTRACT_MILES",""})
            assertThrows(BadRequestException.class, () -> calculator.calculate(assignment(),policy(basis)));
        var p = policy("ACTUAL_ALL_MILES"); p.setPerMileRate(null);
        assertThrows(BadRequestException.class, () -> calculator.calculate(assignment(),p));
        p.setPerMileRate(new BigDecimal("-1")); assertThrows(BadRequestException.class, () -> calculator.calculate(assignment(),p));
        var a = assignment(); a.setActualMiles(new BigDecimal("-1")); assertThrows(BadRequestException.class, () -> calculator.calculate(a,policy("ACTUAL_ALL_MILES")));
    }
    @Test void explicitZeroMilesAndZeroRatesAreValidButMissingSourceIsNot() {
        var a = assignment(); a.setActualMiles(BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"),calculator.calculate(a,policy("ACTUAL_ALL_MILES")).amount());
        var p = policy("ACTUAL_ALL_MILES"); p.setPerMileRate(BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"),calculator.calculate(assignment(),p).amount());
    }
}
