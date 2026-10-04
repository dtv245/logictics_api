package com.company.logicstic.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "trip_driver_assignments",
    schema = "public",
    indexes = {
        @Index(name = "ix_trip_driver_assignments_trip", columnList = "trip_id"),
        @Index(name = "ix_trip_driver_assignments_driver_period", columnList = "driver_id, effective_from, effective_to")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class TripDriverAssignment extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Employee driver;

    @Column(name = "assignment_type", nullable = false, length = 30)
    private String assignmentType = "PRIMARY";

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "planned_miles", precision = 12, scale = 3)
    private BigDecimal plannedMiles;

    @Column(name = "actual_miles", precision = 12, scale = 3)
    private BigDecimal actualMiles;
}
