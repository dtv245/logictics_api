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
@AllArgsConstructor
public class Trip extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @OneToMany(mappedBy = "trip", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<TripStop> stops = new ArrayList<>();
}
