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
@Table(name = "driver_emergency_contacts", schema = "public", indexes = {
        @Index(name = "ix_driver_emergency_contacts_employee_active_created", columnList = "employee_id, active, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class DriverEmergencyContact extends BaseAuditableEntity {
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

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryContact;

    @Column(name = "active", nullable = false)
    private boolean active;
}
