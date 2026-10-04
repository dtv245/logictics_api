package com.company.logicstic.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "load_events",
    schema = "public",
    indexes = {
        @Index(name = "ix_load_events_load_occurred", columnList = "load_id, occurred_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class LoadEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "load_id", nullable = false)
    private Load load;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_stop_id")
    private TripStop tripStop;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "new_status", length = 40)
    private String newStatus;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "source", nullable = false, length = 30)
    private String source = "SYSTEM";

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "note", length = 2000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
