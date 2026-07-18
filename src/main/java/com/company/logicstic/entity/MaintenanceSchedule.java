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
@Table(
    name = "maintenance_schedules",
    schema = "public",
    indexes = {
        @Index(name = "ix_maintenance_schedules_is_active", columnList = "is_active"),
        @Index(name = "ix_maintenance_schedules_next_due_date", columnList = "next_due_date"),
        @Index(name = "ix_maintenance_schedules_truck_id_maintenance_type", columnList = "truck_id,maintenance_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceSchedule extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    @Column(name = "maintenance_type", nullable = false, columnDefinition = "text")
    private String maintenanceType;

    @Column(name = "interval_type", nullable = false, columnDefinition = "text")
    private String intervalType;

    @Column(name = "mileage_interval")
    private Integer mileageInterval;

    @Column(name = "days_interval")
    private Integer daysInterval;

    @Column(name = "engine_hours_interval")
    private Integer engineHoursInterval;

    @Column(name = "last_service_mileage")
    private Integer lastServiceMileage;

    @Column(name = "last_service_date")
    private OffsetDateTime lastServiceDate;

    @Column(name = "last_service_engine_hours")
    private Integer lastServiceEngineHours;

    @Column(name = "next_due_mileage")
    private Integer nextDueMileage;

    @Column(name = "next_due_date")
    private OffsetDateTime nextDueDate;

    @Column(name = "next_due_engine_hours")
    private Integer nextDueEngineHours;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "notes", length = 1000)
    private String notes;
}
