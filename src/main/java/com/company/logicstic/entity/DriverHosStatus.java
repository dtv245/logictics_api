package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "driver_hos_statuses", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DriverHosStatus {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "external_driver_id", length = 100)
    private String externalDriverId;

    @Column(name = "provider_type", nullable = false, columnDefinition = "text")
    private String providerType;

    @Column(name = "current_duty_status", nullable = false, columnDefinition = "text")
    private String currentDutyStatus;

    @Column(name = "status_changed_at", nullable = false)
    private OffsetDateTime statusChangedAt;

    @Column(name = "driving_minutes_remaining", nullable = false)
    private Integer drivingMinutesRemaining;

    @Column(name = "on_duty_minutes_remaining", nullable = false)
    private Integer onDutyMinutesRemaining;

    @Column(name = "cycle_minutes_remaining", nullable = false)
    private Integer cycleMinutesRemaining;

    @Column(name = "time_until_break_required", columnDefinition = "interval")
    private Duration timeUntilBreakRequired;

    @Column(name = "is_in_violation", nullable = false)
    private Boolean isInViolation;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;

    @Column(name = "next_mandatory_break_at")
    private OffsetDateTime nextMandatoryBreakAt;
}
