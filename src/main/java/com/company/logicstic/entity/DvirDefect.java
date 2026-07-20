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
    name = "dvir_defects",
    schema = "public",
    indexes = {
      @Index(name = "ix_dvir_defects_category_severity", columnList = "category,severity"),
      @Index(name = "ix_dvir_defects_corrected_by_id", columnList = "corrected_by_id"),
      @Index(name = "ix_dvir_defects_dvir_report_id", columnList = "dvir_report_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DvirDefect {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dvir_report_id", nullable = false)
  private DvirReport dvirReport;

  @Column(name = "category", nullable = false, columnDefinition = "text")
  private String category;

  @Column(name = "description", nullable = false, length = 1000)
  private String description;

  @Column(name = "severity", nullable = false, columnDefinition = "text")
  private String severity;

  @Column(name = "is_corrected", nullable = false)
  private Boolean isCorrected;

  @Column(name = "correction_notes", length = 1000)
  private String correctionNotes;

  @Column(name = "corrected_at")
  private OffsetDateTime correctedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "corrected_by_id")
  private Employee correctedBy;
}
