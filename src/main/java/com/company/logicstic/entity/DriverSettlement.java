package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "settlements") @Getter @Setter @NoArgsConstructor
public class DriverSettlement {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "settlement_number", nullable = false, unique = true, length = 60) private String settlementNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "driver_id", nullable = false) private Employee driver;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pay_period_id", nullable = false) private PayPeriod payPeriod;
    @Column(name = "settlement_type", nullable = false, length = 20) private String settlementType = "ORIGINAL";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_settlement_id") private DriverSettlement parentSettlement;
    @Column(name = "sequence_number", nullable = false) private Integer sequenceNumber = 0;
    @Column(name = "request_key", length = 120) private String requestKey;
    @Column(name = "validation_reason", length = 1000) private String validationReason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pay_policy_id", nullable = false) private DriverPayPolicy payPolicy;
    @Column(name = "pay_policy_version", nullable = false) private Integer payPolicyVersion;
    @Column(nullable = false, length = 40) private String status = "DRAFT";
    @Column(nullable = false, length = 3) private String currency;
    @Column(name="mileage_pay", nullable=false, precision=19,scale=4) private BigDecimal mileagePay=BigDecimal.ZERO;
    @Column(name="load_pay", nullable=false, precision=19,scale=4) private BigDecimal loadPay=BigDecimal.ZERO;
    @Column(name="percentage_pay", nullable=false, precision=19,scale=4) private BigDecimal percentagePay=BigDecimal.ZERO;
    @Column(name="hourly_pay", nullable=false, precision=19,scale=4) private BigDecimal hourlyPay=BigDecimal.ZERO;
    @Column(name="accessorial_pay", nullable=false, precision=19,scale=4) private BigDecimal accessorialPay=BigDecimal.ZERO;
    @Column(name="bonus_amount", nullable=false, precision=19,scale=4) private BigDecimal bonusAmount=BigDecimal.ZERO;
    @Column(name="reimbursement_amount", nullable=false, precision=19,scale=4) private BigDecimal reimbursementAmount=BigDecimal.ZERO;
    @Column(name="deduction_amount", nullable=false, precision=19,scale=4) private BigDecimal deductionAmount=BigDecimal.ZERO;
    @Column(name="gross_earnings", nullable=false, precision=19,scale=4) private BigDecimal grossEarnings=BigDecimal.ZERO;
    @Column(name="settlement_net", nullable=false, precision=19,scale=4) private BigDecimal settlementNet=BigDecimal.ZERO;
    @Column(name="eligible_miles", precision=19,scale=3) private BigDecimal eligibleMiles;
    @Column(name="loaded_miles", precision=19,scale=3) private BigDecimal loadedMiles;
    @Column(name="empty_miles", precision=19,scale=3) private BigDecimal emptyMiles;
    @Column(name="eligible_hours", precision=19,scale=3) private BigDecimal eligibleHours;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="calculation_snapshot_id", nullable=false) private CalculationSnapshot calculationSnapshot;
    @Column(name="calculated_at") private OffsetDateTime calculatedAt;
    @Column(name="reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="approved_at") private OffsetDateTime approvedAt;
    @Column(name="approved_by") private UUID approvedBy;
    @Column(name="locked_at") private OffsetDateTime lockedAt;
    @Column(name="locked_by") private UUID lockedBy;
    @Column(name="paid_at") private OffsetDateTime paidAt;
    @Version @Column(nullable=false) private Long version;
}
