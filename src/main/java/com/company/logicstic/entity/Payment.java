package com.company.logicstic.entity;

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
    name = "payments",
    schema = "public",
    indexes = {
        @Index(name = "ix_payments_invoice_id", columnList = "invoice_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "stripe_payment_method_id", columnDefinition = "text")
    private String stripePaymentMethodId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "stripe_payment_intent_id", columnDefinition = "text")
    private String stripePaymentIntentId;

    @Column(name = "reference_number", columnDefinition = "text")
    private String referenceNumber;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "amount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountAmount;

    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    @Column(name = "billing_address_city", nullable = false, columnDefinition = "text")
    private String billingAddressCity;

    @Column(name = "billing_address_country", nullable = false, columnDefinition = "text")
    private String billingAddressCountry;

    @Column(name = "billing_address_line1", nullable = false, columnDefinition = "text")
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", columnDefinition = "text")
    private String billingAddressLine2;

    @Column(name = "billing_address_state", nullable = false, columnDefinition = "text")
    private String billingAddressState;

    @Column(name = "billing_address_zip_code", nullable = false, columnDefinition = "text")
    private String billingAddressZipCode;
}
