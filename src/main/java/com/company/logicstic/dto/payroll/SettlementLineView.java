package com.company.logicstic.dto.payroll;

import com.company.logicstic.entity.SettlementLine;
import java.math.BigDecimal;
import java.util.UUID;

public record SettlementLineView(UUID id, String lineType, String lineClass, UUID loadId, UUID tripId,
        String description, BigDecimal quantity, String unit, BigDecimal rate, BigDecimal amount,
        String currency, UUID sourceId) {
    public static SettlementLineView from(SettlementLine l) {
        return new SettlementLineView(l.getId(), l.getLineType(), l.getLineClass(),
                l.getLoad() == null ? null : l.getLoad().getId(), l.getTrip() == null ? null : l.getTrip().getId(),
                l.getDescription(), l.getQuantity(), l.getUnit(), l.getRate(), displayMoney(l.getAmount(),l.getCurrency()), l.getCurrency(), l.getSourceId());
    }
    static BigDecimal displayMoney(BigDecimal amount,String currency) {
        // Canonical presentation removes storage padding without rounding historical financial values.
        return amount==null?null:amount.setScale(Math.max(com.company.logicstic.common.MoneyRoundingPolicy.getScaleForCurrency(currency),
                amount.stripTrailingZeros().scale()));
    }
}
