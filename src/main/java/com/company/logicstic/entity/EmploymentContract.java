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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "employment_contracts", schema = "public", indexes = {
        @Index(name = "ix_employment_contracts_employee_effective", columnList = "employee_id, effective_date, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class EmploymentContract extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "contract_number", nullable = false, length = 100)
    private String contractNumber;

    @Column(name = "contract_type", nullable = false, length = 32)
    private String contractType;

    @Column(name = "signed_date", nullable = false)
    private LocalDate signedDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "termination_reason", length = 1000)
    private String terminationReason;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
