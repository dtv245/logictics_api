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
    name = "driver_behavior_events",
    schema = "public",
    indexes = {
        @Index(name = "ix_driver_behavior_events_employee_id_occurred_at", columnList = "employee_id,occurred_at"),
        @Index(name = "ix_driver_behavior_events_event_type", columnList = "event_type"),
        @Index(name = "ix_driver_behavior_events_external_event_id", columnList = "external_event_id"),
        @Index(name = "ix_driver_behavior_events_reviewed_by_id", columnList = "reviewed_by_id"),
        @Index(name = "ix_driver_behavior_events_truck_id", columnList = "truck_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DriverBehaviorEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @Column(name = "event_type", nullable = false, columnDefinition = "text")
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "provider_type", nullable = false, columnDefinition = "text")
    private String providerType;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "\"location\"", length = 500)
    private String location;

    @Column(name = "speed_mph")
    private Double speedMph;

    @Column(name = "speed_limit_mph")
    private Double speedLimitMph;

    @Column(name = "g_force")
    private Double gForce;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "external_event_id", length = 100)
    private String externalEventId;

    @Column(name = "raw_event_data_json", columnDefinition = "text")
    private String rawEventDataJson;

    @Column(name = "is_reviewed", nullable = false)
    private Boolean isReviewed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Employee reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "is_dismissed")
    private Boolean isDismissed;
}
