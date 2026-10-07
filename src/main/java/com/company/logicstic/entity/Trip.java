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
    name = "trips",
    schema = "public",
    indexes = {
        @Index(name = "ix_trips_truck_id", columnList = "truck_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Trip extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "\"number\"", nullable = false, insertable = false, updatable = false, unique = true)
    private Long number;

    @Column(name = "\"name\"", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "total_distance", nullable = false)
    private Double totalDistance;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "planned_distance_miles", precision = 12, scale = 3)
    private BigDecimal plannedDistanceMiles;

    @Column(name = "actual_distance_miles", precision = 12, scale = 3)
    private BigDecimal actualDistanceMiles;

    @Column(name = "loaded_miles", precision = 12, scale = 3)
    private BigDecimal loadedMiles;

    @Column(name = "empty_miles", precision = 12, scale = 3)
    private BigDecimal emptyMiles;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @OneToMany(mappedBy = "trip", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<TripStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<TripDriverAssignment> driverAssignments = new ArrayList<>();
}
