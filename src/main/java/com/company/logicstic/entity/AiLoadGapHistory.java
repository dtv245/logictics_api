package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "ai_load_gap_history",
    schema = "public",
    indexes = {
        @Index(name = "idx_ai_load_gap_history_gap", columnList = "gap_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AiLoadGapHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gap_id", nullable = false)
    private AiLoadGap gap;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AiLoadGapHistory(AiLoadGap gap, String action, UUID actorId, String actorType, String details) {
        this.gap = gap;
        this.action = action;
        this.actorId = actorId;
        this.actorType = actorType;
        this.details = details;
    }
}
