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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "invoices",
    schema = "public",
    indexes = {
        @Index(name = "ix_invoices_customer_id", columnList = "customer_id"),
        @Index(name = "ix_invoices_employee_id", columnList = "employee_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "\"number\"", nullable = false, insertable = false, updatable = false, unique = true)
    private Long number;

    @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
    private String type;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "tax_behavior", nullable = false, columnDefinition = "text DEFAULT 'exclusive'")
    private String taxBehavior;

    @Column(name = "tax_breakdown_json", columnDefinition = "text")
    private String taxBreakdownJson;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "stripe_invoice_id", columnDefinition = "text")
    private String stripeInvoiceId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "sent_to_email", columnDefinition = "text")
    private String sentToEmail;

    @Column(name = "subtotal_amount", nullable = false, columnDefinition = "numeric")
    private BigDecimal subtotalAmount;

    @Column(name = "subtotal_currency", nullable = false, length = 3)
    private String subtotalCurrency;

    @Column(name = "tax_total_amount", nullable = false, columnDefinition = "numeric")
    private BigDecimal taxTotalAmount;

    @Column(name = "tax_total_currency", nullable = false, length = 3)
    private String taxTotalCurrency;

    @Column(name = "total_amount", nullable = false, columnDefinition = "numeric")
    private BigDecimal totalAmount;

    @Column(name = "invoice_purpose") private String invoicePurpose;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.SMALLINT)
    @Column(name = "economic_sign") private Integer economicSign;
    @Column(name = "rating_snapshot_id") private UUID ratingSnapshotId;
    @Column(name = "parent_invoice_id") private UUID parentInvoiceId;
    @Column(name = "billing_chain_id") private UUID billingChainId;

    /** Legacy invoices keep their verified positive-revenue behavior, without historical backfill. */
    public int economicSign() {
        if(invoicePurpose==null)return 1;
        int expected="CREDIT".equals(invoicePurpose)?-1:1;
        if(!java.util.Set.of("PRIMARY","SUPPLEMENTAL","CREDIT","REBILL").contains(invoicePurpose)
                || economicSign==null || economicSign!=expected)
            throw new com.company.logicstic.exception.BadRequestException("INVOICE_ECONOMIC_SIGN_INVALID","Document requires explicit consistent purpose/economic sign");
        return economicSign;
    }

    @Column(name = "total_currency", nullable = false, length = 3)
    private String totalCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_id")
    private Load load;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @Deprecated(forRemoval = false)
    private Employee employee;

    @OneToMany(
        mappedBy = "invoice",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE},
        orphanRemoval = true
    )
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @Column(name = "period_start")
    @Deprecated(forRemoval = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end")
    @Deprecated(forRemoval = false)
    private OffsetDateTime periodEnd;

    @Column(name = "total_distance_driven")
    @Deprecated(forRemoval = false)
    private Double totalDistanceDriven;

    @Column(name = "total_hours_worked", precision = 10, scale = 2)
    @Deprecated(forRemoval = false)
    private BigDecimal totalHoursWorked;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approval_notes", length = 1000)
    private String approvalNotes;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "billing_period_start")
    private OffsetDateTime billingPeriodStart;

    @Column(name = "billing_period_end")
    private OffsetDateTime billingPeriodEnd;
}
