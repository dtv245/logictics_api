package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.RateRuleRequest;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class RatePolicyValidatorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);
    private RateRuleRequest request(Integer priority, RatingMethod method, RatingMileageBasis basis,
                                    BigDecimal min, BigDecimal max, IndexBasedFscPolicy fsc) {
        return new RateRuleRequest(priority, null, null, null, null, null, null, null, "USD", DATE, DATE,
                method, new BigDecimal("0.123456789123"), basis, min, max, fsc);
    }
    private IndexBasedFscPolicy fsc(BigDecimal mpg, BigDecimal base, String region, Integer age) {
        return new IndexBasedFscPolicy(RatingMileageBasis.CONTRACT_MILES, "EIA", region, age, mpg, base, "USD", "GALLON");
    }
    @Test void flatPreservesExplicitZeroPriorityAndDoesNotRequireMiles() {
        assertDoesNotThrow(() -> RatePolicyValidator.rule(request(0, RatingMethod.FLAT, null, null, null, null)));
    }
    @Test void perMileRequiresBasisAndFlatRejectsIrrelevantBasis() {
        assertThrows(BadRequestException.class, () -> RatePolicyValidator.rule(request(10, RatingMethod.PER_MILE, null, null, null, null)));
        assertThrows(BadRequestException.class, () -> RatePolicyValidator.rule(request(10, RatingMethod.FLAT, RatingMileageBasis.CONTRACT_MILES, null, null, null)));
    }
    @Test void priorityIsNotDefaultedAndInputDecimalsAreNotRounded() {
        assertThrows(BadRequestException.class, () -> RatePolicyValidator.rule(request(null, RatingMethod.FLAT, null, null, null, null)));
        var r = request(-3, RatingMethod.PER_MILE, RatingMileageBasis.ACTUAL_LOADED_MILES, null, null, null);
        RatePolicyValidator.rule(r); assertEquals("0.123456789123", r.baseRate().toPlainString());
    }
    @Test void monetaryFloorAndCapAreOrderedBeforeRounding() {
        assertThrows(BadRequestException.class, () -> RatePolicyValidator.rule(request(1, RatingMethod.FLAT, null, new BigDecimal("2"), BigDecimal.ONE, null)));
        assertDoesNotThrow(() -> RatePolicyValidator.rule(request(1, RatingMethod.FLAT, null, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }
    @Test void missingMpgAndBasePriceHaveExplicitCodes() {
        assertEquals("RATE_MPG_REQUIRED", assertThrows(BadRequestException.class,
                () -> RatePolicyValidator.rule(request(1, RatingMethod.FLAT, null, null, null, fsc(null, BigDecimal.ONE, "US", 7)))).getCode());
        assertEquals("RATE_BASE_FUEL_PRICE_REQUIRED", assertThrows(BadRequestException.class,
                () -> RatePolicyValidator.rule(request(1, RatingMethod.FLAT, null, null, null, fsc(BigDecimal.ONE, null, "US", 7)))).getCode());
    }
    @Test void invalidMpgRegionAndMissingStalePolicyCannotFallback() {
        for (var f : new IndexBasedFscPolicy[] {fsc(BigDecimal.ZERO, BigDecimal.ONE, "US", 7),
                fsc(BigDecimal.ONE, BigDecimal.ONE, "UNSUPPORTED", 7), fsc(BigDecimal.ONE, BigDecimal.ONE, "US", null)})
            assertEquals("INVALID_RATE_POLICY", assertThrows(BadRequestException.class,
                    () -> RatePolicyValidator.rule(request(1, RatingMethod.FLAT, null, null, null, f))).getCode());
    }
    @Test void supportedMethodsAreOnlyApprovedV1Subset() {
        assertArrayEquals(new RatingMethod[] {RatingMethod.FLAT, RatingMethod.PER_MILE}, RatingMethod.values());
        assertThrows(IllegalArgumentException.class, () -> RatingMethod.valueOf("TIERED"));
    }
}
