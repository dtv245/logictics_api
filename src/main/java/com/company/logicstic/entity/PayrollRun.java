package com.company.logicstic.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="payroll_runs") @Getter @Setter @NoArgsConstructor
public class PayrollRun {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @Column(name="run_number",nullable=false,length=60) private String runNumber;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="pay_period_id",nullable=false) private PayPeriod payPeriod;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,length=30) private String status;
 @Column(name="request_key",length=120) private String requestKey;
 @Column(name="effective_date") private LocalDate effectiveDate;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="calculation_input_json",columnDefinition="jsonb") private String calculationInputJson;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="calculation_snapshot_json",columnDefinition="jsonb") private String calculationSnapshotJson;
 @Column(name="validation_reason",length=1000) private String validationReason;
 @Column(name="calculated_at") private OffsetDateTime calculatedAt;
 @Column(name="approved_at") private OffsetDateTime approvedAt;
 @Column(name="approved_by") private UUID approvedBy;
 @Column(name="locked_at") private OffsetDateTime lockedAt;
 @Column(name="locked_by") private UUID lockedBy;
 @Column(name="paid_at") private OffsetDateTime paidAt;
 @Version @Column(nullable=false) private Long version;
}
