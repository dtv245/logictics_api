package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
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
    name = "payment_links",
    schema = "public",
    indexes = {
        @Index(name = "ix_payment_links_expires_at", columnList = "expires_at"),
        @Index(name = "ix_payment_links_invoice_id", columnList = "invoice_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLink extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "\"token\"", nullable = false, unique = true, length = 128)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "access_count", nullable = false)
    private Integer accessCount;

    @Column(name = "last_accessed_at")
    private OffsetDateTime lastAccessedAt;
}
