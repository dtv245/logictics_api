package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "ai_load_gaps",
    schema = "public",
    indexes = {
        @Index(name = "idx_ai_load_gaps_load_status", columnList = "load_id, status"),
        @Index(name = "idx_ai_load_gaps_tenant", columnList = "tenant_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_ai_load_gaps_tenant_load_rule", columnNames = {"tenant_id", "load_id", "rule_code"})
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AiLoadGap {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "load_id", nullable = false)
    private Load load;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "question_template", nullable = false, columnDefinition = "text")
    private String questionTemplate;

    @Column(name = "question_natural", columnDefinition = "text")
    private String questionNatural;

    @Column(name = "proposed_value", columnDefinition = "text")
    private String proposedValue;

    @Column(name = "ask_count", nullable = false)
    private Integer askCount = 0;

    @Column(name = "max_asks", nullable = false)
    private Integer maxAsks = 3;

    @Column(name = "last_asked_at")
    private OffsetDateTime lastAskedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "dismissed_at")
    private OffsetDateTime dismissedAt;

    @Column(name = "dismissed_by")
    private UUID dismissedBy;

    @Column(name = "dismiss_reason", length = 1000)
    private String dismissReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "gap", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AiLoadGapHistory> history = new ArrayList<>();
}
