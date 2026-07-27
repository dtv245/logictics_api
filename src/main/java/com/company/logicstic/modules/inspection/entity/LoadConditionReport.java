package com.company.logicstic.modules.inspection.entity;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "load_condition_reports",
    schema = "public",
    indexes = {
      @Index(name = "ix_load_condition_reports_container_number", columnList = "container_number"),
      @Index(name = "ix_load_condition_reports_inspected_at", columnList = "inspected_at"),
      @Index(name = "ix_load_condition_reports_inspected_by_id", columnList = "inspected_by_id"),
      @Index(name = "ix_load_condition_reports_load_id", columnList = "load_id"),
      @Index(name = "ix_load_condition_reports_vin", columnList = "vin")
    })
@Getter
@Setter
@NoArgsConstructor
public class LoadConditionReport extends BaseAuditableEntity {

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

  @Column(name = "vin", length = 17)
  private String vin;

  @Column(name = "vehicle_year")
  private Integer vehicleYear;

  @Column(name = "vehicle_make", length = 100)
  private String vehicleMake;

  @Column(name = "vehicle_model", length = 100)
  private String vehicleModel;

  @Column(name = "vehicle_body_class", length = 100)
  private String vehicleBodyClass;

  @Column(name = "container_number", length = 20)
  private String containerNumber;

  @Column(name = "seal_number", length = 50)
  private String sealNumber;

  @Column(name = "notes", length = 2000)
  private String notes;

  @Column(name = "inspector_signature", length = 2048)
  private String inspectorSignature;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "inspected_at", nullable = false)
  private OffsetDateTime inspectedAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspected_by_id", nullable = false)
  private Employee inspectedBy;

  @OneToMany(
      mappedBy = "loadConditionReport",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<ConditionDefect> defects = new ArrayList<>();
}
