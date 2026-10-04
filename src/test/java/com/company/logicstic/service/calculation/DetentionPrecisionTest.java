package com.company.logicstic.service.calculation;

import com.company.logicstic.dto.accessorial.DetentionCalculationRequest;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DetentionPrecisionTest {
    final com.company.logicstic.service.accessorial.DetentionCalculator calculator =
            new com.company.logicstic.service.accessorial.DetentionCalculator(TestRoundingPolicies.standard());
    final OffsetDateTime arrival = OffsetDateTime.parse("2026-10-03T10:00:00Z");
    DetentionCalculationRequest policy(int free, int block, String currency) {
        return new DetentionCalculationRequest(free, block, new BigDecimal("60"), new BigDecimal("30"), currency);
    }
    @Test void positiveSecondBeyondFreeTimeStartsOneWholeBlock() {
        var result = calculator.calculate(UUID.randomUUID(), arrival, arrival.plusMinutes(45).plusSeconds(1), policy(45,15,"USD"));
        assertEquals(new BigDecimal("15.00"), result.customerAmount());
        assertEquals(new BigDecimal("7.50"), result.driverPayAmount()); assertEquals(BigDecimal.ONE, result.billableUnits());
        assertEquals(0, result.excessSeconds().compareTo(BigDecimal.ONE));
    }
    @Test void continuousHourPriceDoesNotUseRoundedDisplayUnits() {
        var result = calculator.calculate(UUID.randomUUID(), arrival, arrival.plusSeconds(1), policy(0,0,"USD"));
        assertEquals(new BigDecimal("0.02"), result.customerAmount()); assertEquals(new BigDecimal("0.01"), result.driverPayAmount());
    }
    @Test void exactBlockDoesNotAddAnotherBlock() {
        var result = calculator.calculate(UUID.randomUUID(), arrival, arrival.plusMinutes(60), policy(45,15,"USD"));
        assertEquals(BigDecimal.ONE, result.billableUnits());
    }
    @Test void freeWindowProducesLegitimateZeroInCurrencyScale() {
        var result = calculator.calculate(UUID.randomUUID(), arrival, arrival.plusMinutes(30), policy(45,15,"VND"));
        assertEquals(BigDecimal.ZERO, result.customerAmount());
    }
    @Test void timezoneOffsetsAreComparedAsInstants() {
        var result = calculator.calculate(UUID.randomUUID(), arrival, OffsetDateTime.parse("2026-10-03T18:00:00+07:00"), policy(0,0,"USD"));
        assertEquals(60, result.dwellMinutes()); assertEquals(new BigDecimal("60.00"), result.customerAmount());
    }
    @Test void refusesMissingReverseTimesAndNegativeRates() {
        assertThrows(BadRequestException.class, () -> calculator.calculate(UUID.randomUUID(), null, arrival, policy(0,0,"USD")));
        assertThrows(BadRequestException.class, () -> calculator.calculate(UUID.randomUUID(), arrival, arrival.minusSeconds(1), policy(0,0,"USD")));
        var negative = new DetentionCalculationRequest(0,15,new BigDecimal("-1"),null,"USD");
        assertThrows(BadRequestException.class, () -> calculator.calculate(UUID.randomUUID(), arrival, arrival.plusSeconds(1), negative));
    }
}
