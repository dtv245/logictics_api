package com.company.logicstic.modules.finance.entity;

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
    name = "invoice_line_items",
    schema = "public",
    indexes = {
        @Index(name = "ix_invoice_line_items_invoice_id", columnList = "invoice_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLineItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
    private String type;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "\"order\"", nullable = false)
    private Integer order;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRatePercent;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "amount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountAmount;

    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;
}
