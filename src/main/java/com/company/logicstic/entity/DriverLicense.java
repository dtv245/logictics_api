package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "driver_licenses",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(name = "ix_driver_licenses_employee_id_license_hmac_country", columnNames = {"employee_id", "license_number_hmac", "issuing_country"})
    },
    indexes = {
        @Index(name = "ix_driver_licenses_document_id", columnList = "document_id"),
        @Index(name = "ix_driver_licenses_expires_at", columnList = "expires_at"),
        @Index(name = "ix_driver_licenses_front_image_document_id", columnList = "front_image_document_id"),
        @Index(name = "ix_driver_licenses_back_image_document_id", columnList = "back_image_document_id")
    }
)
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

    @Column(name = "license_number", nullable = false, length = 256)
    private String licenseNumber;

    @Column(name = "license_number_hmac", length = 64)
    private String licenseNumberHmac;

    @Column(name = "license_class", nullable = false, columnDefinition = "text")
    private String licenseClass;

    @Column(name = "endorsements", nullable = false, columnDefinition = "text")
    private String endorsements;

    @Column(name = "issuing_country", nullable = false, length = 2)
    private String issuingCountry;

    @Column(name = "issuing_region", length = 64)
    private String issuingRegion;

    @Column(name = "issued_place", length = 255)
    private String issuedPlace;

    @Column(name = "issued_date", nullable = false)
    private OffsetDateTime issuedDate;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "medical_cert_expires_at")
    private OffsetDateTime medicalCertExpiresAt;

    @Column(name = "remaining_points")
    private Short remainingPoints;

    @Column(name = "status", nullable = false, columnDefinition = "text DEFAULT 'active'")
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "front_image_document_id")
    private Document frontImageDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "back_image_document_id")
    private Document backImageDocument;

    @Column(name = "last_reminder_sent_at")
    private OffsetDateTime lastReminderSentAt;

    @Column(name = "last_reminder_threshold_days")
    private Integer lastReminderThresholdDays;
}
