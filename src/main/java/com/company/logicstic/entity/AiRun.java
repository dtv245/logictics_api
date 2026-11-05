package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_runs", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class AiRun {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "run_type", nullable = false, length = 50)
    private String runType; // 'GAP_EVALUATION', 'DISPATCH_OPTIMIZATION'

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'SUCCESS', 'FAILED', 'FALLBACK'

    @Column(name = "load_id")
    private UUID loadId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens = 0;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens = 0;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs = 0L;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
