package com.company.logicstic.service.calculation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class DetentionCalculatorTest {
    private final DetentionCalculator calculator = new DetentionCalculator(TestRoundingPolicies.standard());
    @Test void noChargeWithinFreeTime() { assertEquals(new BigDecimal("0.00"), calculator.calculate(30, 45, 15, new BigDecimal("25"), "USD")); }
    @Test void roundsUpDetentionBlocks() { assertEquals(new BigDecimal("75.00"), calculator.calculate(90, 45, 15, new BigDecimal("25"), "USD")); }
}
