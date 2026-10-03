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
                l.getDescription(), l.getQuantity(), l.getUnit(), l.getRate(), l.getAmount(), l.getCurrency(), l.getSourceId());
    }
}
