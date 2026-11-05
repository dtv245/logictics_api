package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "driver_profiles", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class DriverProfile extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "marital_status", length = 24)
    private String maritalStatus;

    @Column(name = "education_level", length = 80)
    private String educationLevel;

    /** AES-GCM ciphertext; clear national ID is exposed only by the ADMIN-sensitive view. */
    @Column(name = "national_id_number", length = 512)
    private String nationalIdNumber;

    @Column(name = "national_id_number_hmac", length = 64)
    private String nationalIdNumberHmac;

    @Column(name = "national_id_issued_date")
    private LocalDate nationalIdIssuedDate;

    @Column(name = "national_id_issued_place", length = 255)
    private String nationalIdIssuedPlace;

    /** AES-GCM ciphertext; clear tax code is exposed only by the ADMIN-sensitive view. */
    @Column(name = "personal_tax_code", length = 512)
    private String personalTaxCode;

    @Column(name = "personal_tax_code_hmac", length = 64)
    private String personalTaxCodeHmac;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "permanent_address", length = 1000)
    private String permanentAddress;

    @Column(name = "temporary_address", length = 1000)
    private String temporaryAddress;

    @Column(name = "hometown", length = 255)
    private String hometown;
}
