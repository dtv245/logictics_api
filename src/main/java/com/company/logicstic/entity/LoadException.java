package com.company.logicstic.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "load_exceptions",
    schema = "public",
    indexes = {
      @Index(name = "ix_load_exceptions_load_id", columnList = "load_id"),
      @Index(name = "ix_load_exceptions_resolved_at", columnList = "resolved_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoadException extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "load_id", nullable = false)
  private Load load;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @Column(name = "reason", nullable = false, length = 1000)
  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  @Column(name = "reported_by_id", nullable = false)
  private UUID reportedById;

  @Column(name = "reported_by_name", nullable = false, length = 200)
  private String reportedByName;

  @Column(name = "resolution", length = 1000)
  private String resolution;
}
