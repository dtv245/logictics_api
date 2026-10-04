package com.company.logicstic.service.calculation;

import java.math.RoundingMode;
import com.company.logicstic.common.FinancialRoundingPolicy;

public final class TestRoundingPolicies {
    private TestRoundingPolicies() {}
    public static FinancialRoundingPolicy standard() {
        return new FinancialRoundingPolicy("TEST-V1", RoundingMode.HALF_UP, RoundingMode.HALF_UP, RoundingMode.HALF_UP);
    }
}
