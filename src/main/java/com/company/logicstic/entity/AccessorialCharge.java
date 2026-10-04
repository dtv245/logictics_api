package com.company.logicstic.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accessorial_charges")
@Getter
@Setter
@NoArgsConstructor
public class AccessorialCharge {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_id", nullable = false)
    private Load load;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_stop_id")
    private TripStop tripStop;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "quantity", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "rate", precision = 19, scale = 6)
    private BigDecimal rate;

    @Column(name = "free_quantity", precision = 19, scale = 6)
    private BigDecimal freeQuantity;

    @Column(name = "customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal customerAmount;

    @Column(name = "company_cost_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal companyCostAmount;

    @Column(name = "driver_pay_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal driverPayAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_snapshot_id")
    private CalculationSnapshot calculationSnapshot;

    @Column(name = "note", length = 2000)
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
