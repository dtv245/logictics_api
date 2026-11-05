package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "driver_operational_statuses", schema = "public", indexes = {
        @Index(name = "ix_driver_operational_statuses_availability_employee", columnList = "availability, employee_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DriverOperationalStatus extends BaseAuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "availability", nullable = false, length = 20)
    private String availability;

    @Column(name = "default_vehicle_id")
    private UUID defaultVehicleId;

    @Column(name = "last_known_latitude", precision = 10, scale = 7)
    private BigDecimal lastKnownLatitude;

    @Column(name = "last_known_longitude", precision = 10, scale = 7)
    private BigDecimal lastKnownLongitude;

    @Column(name = "last_known_at")
    private OffsetDateTime lastKnownAt;

    @Column(name = "leave_balance_days", precision = 6, scale = 2)
    private BigDecimal leaveBalanceDays;
}
