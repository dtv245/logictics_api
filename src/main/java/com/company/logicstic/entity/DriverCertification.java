package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "driver_certifications", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class DriverCertification extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issuer", length = 255)
    private String issuer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
