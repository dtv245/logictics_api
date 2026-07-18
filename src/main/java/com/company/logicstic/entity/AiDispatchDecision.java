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
    name = "ai_dispatch_decisions",
    schema = "public",
    indexes = {
        @Index(name = "ix_ai_dispatch_decisions_session_id", columnList = "session_id"),
        @Index(name = "ix_ai_dispatch_decisions_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDispatchDecision {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiDispatchSession session;

    @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
    private String type;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "reasoning", nullable = false, length = 4000)
    private String reasoning;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_input", columnDefinition = "text")
    private String toolInput;

    @Column(name = "tool_output", columnDefinition = "text")
    private String toolOutput;

    @Column(name = "load_id")
    private UUID loadId;

    @Column(name = "truck_id")
    private UUID truckId;

    @Column(name = "trip_id")
    private UUID tripId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
