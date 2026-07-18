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
    name = "expenses",
    schema = "public",
    indexes = {
        @Index(name = "ix_expenses_expense_date", columnList = "expense_date"),
        @Index(name = "ix_expenses_status", columnList = "status"),
        @Index(name = "ix_expenses_truck_id", columnList = "truck_id"),
        @Index(name = "ix_expenses_truck_id1", columnList = "truck_expense_truck_id"),
        @Index(name = "ix_expenses_type", columnList = "type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expense extends BaseAuditableEntity {

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

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Column(name = "expense_date", nullable = false)
    private OffsetDateTime expenseDate;

    @Column(name = "receipt_blob_path", length = 500)
    private String receiptBlobPath;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "approved_by_id", length = 50)
    private String approvedById;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "amount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountAmount;

    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @Column(name = "vendor_address", length = 500)
    private String vendorAddress;

    @Column(name = "vendor_phone", length = 20)
    private String vendorPhone;

    @Column(name = "repair_description", length = 2000)
    private String repairDescription;

    @Column(name = "estimated_completion_date")
    private OffsetDateTime estimatedCompletionDate;

    @Column(name = "actual_completion_date")
    private OffsetDateTime actualCompletionDate;

    @Column(name = "category", columnDefinition = "text")
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_expense_truck_id")
    private Truck truckExpenseTruck;

    @Column(name = "truck_expense_category", columnDefinition = "text")
    private String truckExpenseCategory;

    @Column(name = "odometer_reading")
    private Integer odometerReading;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "quantity_unit", columnDefinition = "text")
    private String quantityUnit;
}
