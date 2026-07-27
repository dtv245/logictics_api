package com.company.logicstic.modules.finance.entity;

import com.company.logicstic.modules.customer.entity.Customer;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "invoices",
    schema = "public",
    indexes = {
      @Index(name = "ix_invoices_customer_id", columnList = "customer_id"),
      @Index(name = "ix_invoices_employee_id", columnList = "employee_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(
      name = "\"number\"",
      nullable = false,
      insertable = false,
      updatable = false,
      unique = true)
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

  @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal subtotalAmount;

  @Column(name = "subtotal_currency", nullable = false, length = 3)
  private String subtotalCurrency;

  @Column(name = "tax_total_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal taxTotalAmount;

  @Column(name = "tax_total_currency", nullable = false, length = 3)
  private String taxTotalCurrency;

  @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "total_currency", nullable = false, length = 3)
  private String totalCurrency;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "load_id", unique = true)
  private Load load;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id")
  private Employee employee;

  @OneToMany(
      mappedBy = "invoice",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<InvoiceLineItem> lineItems = new ArrayList<>();

  @Column(name = "period_start")
  private OffsetDateTime periodStart;

  @Column(name = "period_end")
  private OffsetDateTime periodEnd;

  @Column(name = "total_distance_driven")
  private Double totalDistanceDriven;

  @Column(name = "total_hours_worked", precision = 10, scale = 2)
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
