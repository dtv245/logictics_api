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
    name = "hos_violations",
    schema = "public",
    indexes = {
      @Index(
          name = "ix_hos_violations_employee_id_violation_date",
          columnList = "employee_id,violation_date"),
      @Index(name = "ix_hos_violations_external_violation_id", columnList = "external_violation_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HosViolation {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "violation_date", nullable = false)
  private OffsetDateTime violationDate;

  @Column(name = "violation_type", nullable = false, columnDefinition = "text")
  private String violationType;

  @Column(name = "description", nullable = false, length = 1000)
  private String description;

  @Column(name = "severity_level", nullable = false)
  private Integer severityLevel;

  @Column(name = "is_resolved", nullable = false)
  private Boolean isResolved;

  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  @Column(name = "external_violation_id", length = 100)
  private String externalViolationId;

  @Column(name = "provider_type", nullable = false, columnDefinition = "text")
  private String providerType;

  @Column(name = "rule_set_code", nullable = false, length = 32)
  private String ruleSetCode;
}
