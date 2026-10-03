package com.company.logicstic.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="payroll_payments") @Getter @Setter @NoArgsConstructor
public class PayrollPayment {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payroll_run_item_id",nullable=false) private PayrollRunItem item;
 @Column(name="attempt_number",nullable=false) private Integer attemptNumber;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="payment_method",nullable=false,length=30) private String paymentMethod;
 @Column(nullable=false,length=30) private String status;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="provider_key",length=100) private String providerKey;
 @Column(name="destination_reference",length=200) private String destinationReference;
 @Column(name="provider_reference",length=200) private String providerReference;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="request_snapshot_json",columnDefinition="jsonb") private String requestSnapshotJson;
 @Column(name="failure_code",length=100) private String failureCode;
 @Column(name="failure_message",length=1000) private String failureMessage;
 @Column(name="scheduled_at") private OffsetDateTime scheduledAt;
 @Column(name="scheduled_by") private UUID scheduledBy;
 @Column(name="dispatch_started_at") private OffsetDateTime dispatchStartedAt;
 @Column(name="submitted_at") private OffsetDateTime submittedAt;
 @Column(name="succeeded_at") private OffsetDateTime succeededAt;
 @Column(name="reconciled_at") private OffsetDateTime reconciledAt;
 @Column(name="reconciled_by") private UUID reconciledBy;
 @Column(name="reconciliation_reference",length=200) private String reconciliationReference;
}
