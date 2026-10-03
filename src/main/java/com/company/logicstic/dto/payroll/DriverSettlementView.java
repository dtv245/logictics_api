package com.company.logicstic.dto.payroll;

import com.company.logicstic.entity.DriverSettlement;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DriverSettlementView(UUID id, String settlementNumber, UUID driverId, UUID payPeriodId,
        String settlementType, String status, String currency, BigDecimal grossEarnings,
        BigDecimal reimbursementAmount, BigDecimal deductionAmount, BigDecimal settlementNet,
        OffsetDateTime calculatedAt, OffsetDateTime approvedAt, OffsetDateTime lockedAt,
        List<SettlementLineView> lines, UUID parentSettlementId, Integer sequenceNumber, UUID policyId,
        Integer policyVersion, String validationReason) {
    public static DriverSettlementView from(DriverSettlement s, List<SettlementLineView> lines) {
        return new DriverSettlementView(s.getId(), s.getSettlementNumber(), s.getDriver().getId(), s.getPayPeriod().getId(),
                s.getSettlementType(), s.getStatus(), s.getCurrency(), s.getGrossEarnings(), s.getReimbursementAmount(),
                s.getDeductionAmount(), s.getSettlementNet(), s.getCalculatedAt(), s.getApprovedAt(), s.getLockedAt(), lines,
                s.getParentSettlement()==null?null:s.getParentSettlement().getId(),s.getSequenceNumber(),s.getPayPolicy().getId(),s.getPayPolicyVersion(),s.getValidationReason());
    }
}
