package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.payroll.WorkPayCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkPayCalculatorTest {
    final WorkPayCalculator calculator = new WorkPayCalculator(TestRoundingPolicies.standard());
    DriverPayPolicy policy(String method) {
        var p = new DriverPayPolicy(); p.setId(UUID.randomUUID()); p.setPolicyVersion(1); p.setPayMethod(method); p.setCurrency("USD");
        p.setPerLoadRate(new BigDecimal("125")); p.setHourlyRate(new BigDecimal("20.25")); p.setDailyRate(new BigDecimal("150")); p.setFlatRate(new BigDecimal("1000")); return p;
    }
    @Test void perLoadDailyAndFlatUseExplicitSourceAndSingleEligibleUnit() {
        var source = UUID.randomUUID();
        assertEquals(new BigDecimal("125.00"),calculator.perLoad(source,policy("PER_LOAD")).amount());
        assertEquals(new BigDecimal("150.00"),calculator.daily(source,policy("DAILY")).amount());
        var flat = calculator.flat(source,policy("FLAT_RATE")); assertEquals(new BigDecimal("1000.00"),flat.amount()); assertEquals("PAY_PERIOD",flat.sourceType());
        assertEquals(source,flat.sourceId()); assertEquals(1,flat.policyVersion());
    }
    @Test void hourlyUsesExplicitTotalHoursAndPreservesRawInputAndRoundingVersion() {
        var entry = new TimeEntry(); entry.setId(UUID.randomUUID()); entry.setTotalHours(new BigDecimal("7.25"));
        var result = calculator.hourly(entry,policy("HOURLY")); assertEquals(new BigDecimal("146.81"),result.amount());
        assertEquals(new BigDecimal("146.8125"),result.rawAmount()); assertEquals(entry.getId(),result.sourceId());
        assertEquals(new BigDecimal("7.25"),result.eligibleQuantity()); assertNotNull(result.roundingPolicyVersion());
    }
    @Test void missingNegativeOrMismatchedInputsFailAndExplicitZeroHoursRemainValid() {
        var entry = new TimeEntry(); entry.setId(UUID.randomUUID());
        assertThrows(BadRequestException.class, () -> calculator.hourly(entry,policy("HOURLY")));
        entry.setTotalHours(new BigDecimal("-1")); assertThrows(BadRequestException.class, () -> calculator.hourly(entry,policy("HOURLY")));
        entry.setTotalHours(BigDecimal.ZERO); assertEquals(new BigDecimal("0.00"),calculator.hourly(entry,policy("HOURLY")).amount());
        assertThrows(BadRequestException.class, () -> calculator.perLoad(null,policy("PER_LOAD")));
        assertThrows(BadRequestException.class, () -> calculator.perLoad(UUID.randomUUID(),policy("DAILY")));
    }
}
