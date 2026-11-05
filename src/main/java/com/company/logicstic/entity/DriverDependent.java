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
@Table(name = "driver_dependents", schema = "public", indexes = {
        @Index(name = "ix_driver_dependents_employee_active_created", columnList = "employee_id, active, created_at"),
        @Index(name = "ix_driver_dependents_national_id_hmac", columnList = "national_id_number_hmac"),
        @Index(name = "ix_driver_dependents_tax_code_hmac", columnList = "tax_code_hmac")
})
@Getter
@Setter
@NoArgsConstructor
public class DriverDependent extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "relationship", nullable = false, length = 64)
    private String relationship;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** AES-GCM ciphertext; clear national ID is exposed only by the ADMIN-sensitive view. */
    @Column(name = "national_id_number", length = 512)
    private String nationalIdNumber;

    @Column(name = "national_id_number_hmac", length = 64)
    private String nationalIdNumberHmac;

    /** AES-GCM ciphertext; clear tax code is exposed only by the ADMIN-sensitive view. */
    @Column(name = "tax_code", length = 512)
    private String taxCode;

    @Column(name = "tax_code_hmac", length = 64)
    private String taxCodeHmac;

    @Column(name = "deduction_start_date")
    private LocalDate deductionStartDate;

    @Column(name = "deduction_end_date")
    private LocalDate deductionEndDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
