package com.company.logicstic.modules.ai.entity;

import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "ai_dispatch_sessions", schema = "public")
@Getter
@Setter
@NoArgsConstructor
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

  @Column(name = "request_cost", nullable = false, columnDefinition = "int4 DEFAULT 1")
  private Integer requestCost;

  @Column(name = "is_overage", nullable = false)
  private Boolean isOverage;

  @OneToMany(
      mappedBy = "session",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<AiDispatchDecision> decisions = new ArrayList<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AiDispatchSession that = (AiDispatchSession) o;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
