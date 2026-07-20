package com.company.logicstic.modules.employee.entity;

import com.company.logicstic.shared.BaseAuditableEntity;
import com.company.logicstic.modules.role.entity.TenantRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "employees",
    schema = "public",
    indexes = {
        @Index(name = "ix_employees_role_id", columnList = "role_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Employee extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, columnDefinition = "text")
    private String email;

    @Column(name = "first_name", nullable = false, columnDefinition = "text")
    private String firstName;

    @Column(name = "last_name", nullable = false, columnDefinition = "text")
    private String lastName;

    @Column(name = "phone_number", columnDefinition = "text")
    private String phoneNumber;

    @Column(name = "salary_type", nullable = false, columnDefinition = "text")
    private String salaryType;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "joined_date", nullable = false)
    private OffsetDateTime joinedDate;

    @Column(name = "device_token", columnDefinition = "text")
    private String deviceToken;

    @Column(name = "stripe_connected_account_id", columnDefinition = "text")
    private String stripeConnectedAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private TenantRole role;

    @Column(name = "address_city", columnDefinition = "text")
    private String addressCity;

    @Column(name = "address_country", columnDefinition = "text")
    private String addressCountry;

    @Column(name = "address_line1", columnDefinition = "text")
    private String addressLine1;

    @Column(name = "address_line2", columnDefinition = "text")
    private String addressLine2;

    @Column(name = "address_state", columnDefinition = "text")
    private String addressState;

    @Column(name = "address_zip_code", columnDefinition = "text")
    private String addressZipCode;

    @Column(name = "salary_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal salaryAmount;

    @Column(name = "salary_currency", nullable = false, length = 3)
    private String salaryCurrency;
}
