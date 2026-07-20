package com.company.logicstic.modules.employee.entity;

import jakarta.persistence.*;
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
@Table(
    name = "hos_logs",
    schema = "public",
    indexes = {
        @Index(name = "ix_hos_logs_employee_id_log_date", columnList = "employee_id,log_date"),
        @Index(name = "ix_hos_logs_external_log_id", columnList = "external_log_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class HosLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "log_date", nullable = false)
    private OffsetDateTime logDate;

    @Column(name = "duty_status", nullable = false, columnDefinition = "text")
    private String dutyStatus;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "\"location\"", length = 500)
    private String location;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "remark", length = 1000)
    private String remark;

    @Column(name = "external_log_id", length = 100)
    private String externalLogId;

    @Column(name = "provider_type", nullable = false, columnDefinition = "text")
    private String providerType;
}
