package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "driver_violations", schema = "public", indexes = {
        @Index(name = "ix_driver_violations_employee_date", columnList = "employee_id, violation_date, id"),
        @Index(name = "ix_driver_violations_trip_id", columnList = "trip_id"),
        @Index(name = "ix_driver_violations_document_id", columnList = "document_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DriverViolation extends BaseAuditableEntity {
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

    @Column(name = "violation_type", nullable = false, length = 100)
    private String violationType;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Column(name = "fine_amount", precision = 18, scale = 2)
    private BigDecimal fineAmount;

    @Column(name = "fine_currency", nullable = false, length = 3)
    private String fineCurrency = "VND";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "resolution_status", nullable = false, length = 24)
    private String resolutionStatus = "OPEN";

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
