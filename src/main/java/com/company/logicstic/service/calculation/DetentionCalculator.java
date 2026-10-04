package com.company.logicstic.service.calculation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.company.logicstic.common.FinancialRoundingPolicy;
import lombok.RequiredArgsConstructor;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.stereotype.Service;
@Service("detentionCalculationCore")
@RequiredArgsConstructor
public class DetentionCalculator {
    private final FinancialRoundingPolicy rounding;
    public BigDecimal calculate(long dwellMinutes, long freeMinutes, long blockMinutes, BigDecimal ratePerBlock, String currency) {
        if (dwellMinutes < 0 || freeMinutes < 0 || blockMinutes <= 0 || ratePerBlock == null || ratePerBlock.signum() < 0) {
            throw new BadRequestException("DETENTION_POLICY_INVALID", "Dwell, policy minutes, and rate must be valid");
        }
        long excess = Math.max(0, dwellMinutes - freeMinutes);
        BigDecimal blocks = BigDecimal.valueOf(excess).divide(BigDecimal.valueOf(blockMinutes), 0, RoundingMode.CEILING);
        return rounding.money(ratePerBlock.multiply(blocks), currency, FinancialRoundingPolicy.Boundary.INVOICE);
    }
}
