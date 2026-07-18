package com.company.logicstic.entity;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "maintenance_records",
    schema = "public",
    indexes = {
        @Index(name = "ix_maintenance_records_maintenance_schedule_id", columnList = "maintenance_schedule_id"),
        @Index(name = "ix_maintenance_records_maintenance_type", columnList = "maintenance_type"),
        @Index(name = "ix_maintenance_records_performed_by_id", columnList = "performed_by_id"),
        @Index(name = "ix_maintenance_records_truck_id_service_date", columnList = "truck_id,service_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceRecord extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_schedule_id")
    private MaintenanceSchedule maintenanceSchedule;

    @Column(name = "maintenance_type", nullable = false, columnDefinition = "text")
    private String maintenanceType;

    @Column(name = "service_date", nullable = false)
    private OffsetDateTime serviceDate;

    @Column(name = "odometer_reading", nullable = false)
    private Integer odometerReading;

    @Column(name = "engine_hours")
    private Integer engineHours;

    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    @Column(name = "vendor_address", length = 500)
    private String vendorAddress;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "labor_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal laborCost;

    @Column(name = "parts_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal partsCost;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "work_performed", length = 2000)
    private String workPerformed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    private Employee performedBy;

    @OneToMany(
        mappedBy = "maintenanceRecord",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE},
        orphanRemoval = true
    )
    private List<MaintenancePart> parts = new ArrayList<>();
}
