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
    name = "documents",
    schema = "public",
    indexes = {
      @Index(name = "ix_documents_accident_report_id", columnList = "accident_report_id"),
      @Index(name = "ix_documents_dvir_report_id", columnList = "dvir_report_id"),
      @Index(name = "ix_documents_employee_id", columnList = "employee_id"),
      @Index(
          name = "ix_documents_load_condition_report_id",
          columnList = "load_condition_report_id"),
      @Index(name = "ix_documents_load_id", columnList = "load_id"),
      @Index(name = "ix_documents_maintenance_record_id", columnList = "maintenance_record_id"),
      @Index(name = "ix_documents_trip_stop_id", columnList = "trip_stop_id"),
      @Index(name = "ix_documents_truck_id", columnList = "truck_id"),
      @Index(name = "ix_documents_uploaded_by_id", columnList = "uploaded_by_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "owner_type", nullable = false, columnDefinition = "text")
  private String ownerType;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "content_type", nullable = false, length = 128)
  private String contentType;

  @Column(name = "file_size_bytes", nullable = false)
  private Long fileSizeBytes;

  @Column(name = "blob_path", nullable = false, length = 512)
  private String blobPath;

  @Column(name = "blob_container", nullable = false, length = 128)
  private String blobContainer;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "description", length = 1000)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by_id", nullable = false)
  private Employee uploadedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id")
  private Employee employee;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "load_id")
  private Load load;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "load_condition_report_id")
  private LoadConditionReport loadConditionReport;

  @Column(name = "recipient_name", length = 255)
  private String recipientName;

  @Column(name = "recipient_signature", length = 2048)
  private String recipientSignature;

  @Column(name = "capture_latitude")
  private Double captureLatitude;

  @Column(name = "capture_longitude")
  private Double captureLongitude;

  @Column(name = "captured_at")
  private OffsetDateTime capturedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_stop_id")
  private TripStop tripStop;

  @Column(name = "notes", length = 2000)
  private String notes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "truck_id")
  private Truck truck;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "accident_report_id")
  private AccidentReport accidentReport;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dvir_report_id")
  private DvirReport dvirReport;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "maintenance_record_id")
  private MaintenanceRecord maintenanceRecord;
}
