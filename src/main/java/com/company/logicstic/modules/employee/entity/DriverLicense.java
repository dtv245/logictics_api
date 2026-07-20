package com.company.logicstic.modules.employee.entity;

import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "driver_licenses",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_driver_licenses_employee_id_license_number_issuing_country",
          columnNames = {"employee_id", "license_number", "issuing_country"})
    },
    indexes = {
      @Index(name = "ix_driver_licenses_document_id", columnList = "document_id"),
      @Index(name = "ix_driver_licenses_expires_at", columnList = "expires_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class DriverLicense extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "license_number", nullable = false, length = 64)
  private String licenseNumber;

  @Column(name = "license_class", nullable = false, columnDefinition = "text")
  private String licenseClass;

  @Column(name = "endorsements", nullable = false, columnDefinition = "text")
  private String endorsements;

  @Column(name = "issuing_country", nullable = false, length = 2)
  private String issuingCountry;

  @Column(name = "issuing_region", length = 64)
  private String issuingRegion;

  @Column(name = "issued_date", nullable = false)
  private OffsetDateTime issuedDate;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "medical_cert_expires_at")
  private OffsetDateTime medicalCertExpiresAt;

  @Column(name = "status", nullable = false, columnDefinition = "text DEFAULT 'active'")
  private String status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id")
  private Document document;

  @Column(name = "last_reminder_sent_at")
  private OffsetDateTime lastReminderSentAt;

  @Column(name = "last_reminder_threshold_days")
  private Integer lastReminderThresholdDays;
}
