package com.company.logicstic.modules.fleet.entity;

import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "containers",
    schema = "public",
    indexes = {
        @Index(name = "ix_containers_current_terminal_id", columnList = "current_terminal_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Container extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "\"number\"", nullable = false, unique = true, length = 11)
    private String number;

    @Column(name = "iso_type", nullable = false, columnDefinition = "text")
    private String isoType;

    @Column(name = "seal_number", length = 50)
    private String sealNumber;

    @Column(name = "booking_reference", length = 100)
    private String bookingReference;

    @Column(name = "bill_of_lading_number", length = 100)
    private String billOfLadingNumber;

    @Column(name = "is_laden", nullable = false)
    private Boolean isLaden;

    @Column(name = "gross_weight", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossWeight;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_terminal_id")
    private Terminal currentTerminal;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;
}
