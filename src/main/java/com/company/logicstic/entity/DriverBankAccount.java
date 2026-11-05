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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "driver_bank_accounts", schema = "public", indexes = {
        @Index(name = "ix_driver_bank_accounts_employee_active_created", columnList = "employee_id, active, created_at"),
        @Index(name = "ix_driver_bank_accounts_account_number_hmac", columnList = "account_number_hmac")
})
@Getter
@Setter
@NoArgsConstructor
public class DriverBankAccount extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "bank_name", nullable = false, length = 128)
    private String bankName;

    /** AES-GCM ciphertext; clear account number is exposed only by the ADMIN-sensitive view. */
    @Column(name = "account_number", nullable = false, length = 512)
    private String accountNumber;

    @Column(name = "account_number_hmac", nullable = false, length = 64)
    private String accountNumberHmac;

    @Column(name = "account_holder_name", nullable = false, length = 255)
    private String accountHolderName;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
