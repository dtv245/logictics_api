package com.company.logicstic.modules.inspection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "condition_defects",
    schema = "public",
    indexes = {
      @Index(
          name = "ix_condition_defects_load_condition_report_id",
          columnList = "load_condition_report_id"),
      @Index(name = "ix_condition_defects_part_category", columnList = "part_category")
    })
@Getter
@Setter
@NoArgsConstructor
public class ConditionDefect {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "load_condition_report_id", nullable = false)
  private LoadConditionReport loadConditionReport;

  @Column(name = "part_category", nullable = false, length = 64)
  private String partCategory;

  @Column(name = "description", nullable = false, length = 1000)
  private String description;

  @Column(name = "severity", nullable = false, length = 32)
  private String severity;
}
