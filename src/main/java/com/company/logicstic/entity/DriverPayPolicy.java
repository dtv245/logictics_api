package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "driver_pay_policies")
@Getter @Setter @NoArgsConstructor
public class DriverPayPolicy {
    @Id @GeneratedValue @UuidGenerator @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "policy_code", nullable = false, length = 80)
    private String policyCode;
    @Column(nullable = false, length = 200)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "driver_id")
    private Employee driver;
    @Column(name = "pay_method", nullable = false, length = 40)
    private String payMethod;
    @Column(name = "per_mile_rate", precision = 19, scale = 6) private BigDecimal perMileRate;
    @Column(name = "per_load_rate", precision = 19, scale = 4) private BigDecimal perLoadRate;
    @Column(name = "hourly_rate", precision = 19, scale = 6) private BigDecimal hourlyRate;
    @Column(name = "daily_rate", precision = 19, scale = 4) private BigDecimal dailyRate;
    @Column(name = "flat_rate", precision = 19, scale = 4) private BigDecimal flatRate;
    @Column(name = "revenue_percentage", precision = 9, scale = 6) private BigDecimal revenuePercentage;
    @Column(name = "mileage_basis", length = 40) private String mileageBasis;
    @Column(name = "revenue_basis", length = 50) private String revenueBasis;
    @Column(name = "detention_rate", precision = 19, scale = 6) private BigDecimal detentionRate;
    @Column(name = "detention_free_minutes") private Integer detentionFreeMinutes;
    @Column(name = "detention_block_minutes") private Integer detentionBlockMinutes;
    @Column(name = "layover_rate", precision = 19, scale = 4) private BigDecimal layoverRate;
    @Column(name = "stop_pay_rate", precision = 19, scale = 4) private BigDecimal stopPayRate;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "policy_version", nullable = false) private Integer policyVersion = 1;
    @Column(nullable = false) private Boolean active = true;
    @Version @Column(nullable = false) private Long version;
}
