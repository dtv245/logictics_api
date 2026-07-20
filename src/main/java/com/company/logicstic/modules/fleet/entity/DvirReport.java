package com.company.logicstic.modules.fleet.entity;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.trip.entity.Trip;
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
    name = "dvir_reports",
    schema = "public",
    indexes = {
      @Index(
          name = "ix_dvir_reports_driver_id_inspection_date",
          columnList = "driver_id,inspection_date"),
      @Index(name = "ix_dvir_reports_reviewed_by_id", columnList = "reviewed_by_id"),
      @Index(name = "ix_dvir_reports_status", columnList = "status"),
      @Index(name = "ix_dvir_reports_trip_id", columnList = "trip_id"),
      @Index(
          name = "ix_dvir_reports_truck_id_inspection_date",
          columnList = "truck_id,inspection_date")
    })
@Getter
@Setter
@NoArgsConstructor
public class DvirReport extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "truck_id", nullable = false)
  private Truck truck;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "driver_id", nullable = false)
  private Employee driver;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "inspection_date", nullable = false)
  private OffsetDateTime inspectionDate;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "odometer_reading")
  private Integer odometerReading;

  @Column(name = "has_defects", nullable = false)
  private Boolean hasDefects;

  @Column(name = "driver_signature", columnDefinition = "text")
  private String driverSignature;

  @Column(name = "driver_notes", length = 2000)
  private String driverNotes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by_id")
  private Employee reviewedBy;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Column(name = "mechanic_signature", columnDefinition = "text")
  private String mechanicSignature;

  @Column(name = "mechanic_notes", length = 2000)
  private String mechanicNotes;

  @Column(name = "defects_corrected")
  private Boolean defectsCorrected;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @OneToMany(
      mappedBy = "dvirReport",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<DvirDefect> defects = new ArrayList<>();
}
