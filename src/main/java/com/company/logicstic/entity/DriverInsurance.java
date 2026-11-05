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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "driver_insurances", schema = "public", indexes = {
        @Index(name = "ix_driver_insurances_employee_period", columnList = "employee_id, participation_start_date, created_at"),
        @Index(name = "ix_driver_insurances_social_number_hmac", columnList = "social_insurance_number_hmac"),
        @Index(name = "ix_driver_insurances_health_card_hmac", columnList = "health_insurance_card_number_hmac")
})
@Getter
@Setter
@NoArgsConstructor
public class DriverInsurance extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** AES-GCM ciphertext; clear value is exposed only through the ADMIN-sensitive view. */
    @Column(name = "social_insurance_number", length = 512)
    private String socialInsuranceNumber;

    @Column(name = "social_insurance_number_hmac", length = 64)
    private String socialInsuranceNumberHmac;

    /** AES-GCM ciphertext; clear value is exposed only through the ADMIN-sensitive view. */
    @Column(name = "health_insurance_card_number", length = 512)
    private String healthInsuranceCardNumber;

    @Column(name = "health_insurance_card_number_hmac", length = 64)
    private String healthInsuranceCardNumberHmac;

    @Column(name = "registered_clinic", length = 255)
    private String registeredClinic;

    @Column(name = "participation_start_date", nullable = false)
    private LocalDate participationStartDate;

    @Column(name = "participation_end_date")
    private LocalDate participationEndDate;

    @Column(name = "social_insurance_status", length = 32)
    private String socialInsuranceStatus;

    @Column(name = "health_insurance_status", length = 32)
    private String healthInsuranceStatus;

    @Column(name = "unemployment_insurance_status", length = 32)
    private String unemploymentInsuranceStatus;

    @Column(name = "insured_salary", precision = 18, scale = 2)
    private BigDecimal insuredSalary;

    @Column(name = "health_insurance_expiry_date")
    private LocalDate healthInsuranceExpiryDate;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
