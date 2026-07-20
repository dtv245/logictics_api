package com.company.logicstic.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "ai_dispatch_sessions", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDispatchSession extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(
      name = "\"number\"",
      nullable = false,
      insertable = false,
      updatable = false,
      unique = true)
  private Long number;

  @Column(name = "\"mode\"", nullable = false, columnDefinition = "text")
  private String mode;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "triggered_by_user_id")
  private UUID triggeredByUserId;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "instructions", columnDefinition = "text")
  private String instructions;

  @Column(name = "input_tokens_used", nullable = false)
  private Integer inputTokensUsed;

  @Column(name = "output_tokens_used", nullable = false)
  private Integer outputTokensUsed;

  @Column(name = "cache_read_tokens", nullable = false)
  private Integer cacheReadTokens;

  @Column(name = "cache_creation_tokens", nullable = false)
  private Integer cacheCreationTokens;

  @Column(name = "estimated_cost_usd", nullable = false)
  private BigDecimal estimatedCostUsd;

  @Column(name = "model_used", columnDefinition = "text")
  private String modelUsed;

  @Column(name = "decision_count", nullable = false)
  private Integer decisionCount;

  @Column(name = "summary", length = 4000)
  private String summary;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "request_cost", nullable = false)
  private Integer requestCost;

  @Column(name = "is_overage", nullable = false)
  private Boolean isOverage;
}
