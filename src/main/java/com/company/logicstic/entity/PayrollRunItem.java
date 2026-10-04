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

@Entity @Table(name="payroll_run_items") @Getter @Setter @NoArgsConstructor
public class PayrollRunItem {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payroll_run_id",nullable=false) private PayrollRun payrollRun;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="driver_id",nullable=false) private Employee driver;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="gross_amount",nullable=false,precision=19,scale=4) private BigDecimal grossAmount;
 @Column(name="income_tax_amount",precision=19,scale=4) private BigDecimal incomeTaxAmount;
 @Column(name="insurance_amount",precision=19,scale=4) private BigDecimal insuranceAmount;
 @Column(name="other_deduction_amount",nullable=false,precision=19,scale=4) private BigDecimal otherDeductionAmount;
 @Column(name="reimbursement_amount",nullable=false,precision=19,scale=4) private BigDecimal reimbursementAmount;
 @Column(name="net_amount",precision=19,scale=4) private BigDecimal netAmount;
 @Column(nullable=false,length=30) private String status;
 @Column(name="tax_availability",nullable=false,length=30) private String taxAvailability;
 @Column(name="validation_reason",length=1000) private String validationReason;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="jurisdiction_id") private PayrollJurisdictionEntity jurisdiction;
 @Enumerated(EnumType.STRING) @Column(name="worker_classification",length=30)
 private com.company.logicstic.service.payroll.domain.WorkerClassification workerClassification;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payroll_policy_id") private PayrollPolicyVersion payrollPolicy;
 @Column(name="payroll_policy_version") private Integer payrollPolicyVersion;
 @Column(name="effective_date") private LocalDate effectiveDate;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="calculation_snapshot_json",columnDefinition="jsonb") private String calculationSnapshotJson;
 @Column(name="no_payment_required_at") private OffsetDateTime noPaymentRequiredAt;
 @Column(name="no_payment_required_by") private UUID noPaymentRequiredBy;
 @Column(name="no_payment_reason_code",length=40) private String noPaymentReasonCode;
 @Column(name="no_payment_reason",length=1000) private String noPaymentReason;
 @ManyToMany @JoinTable(name="payroll_run_item_settlements",joinColumns=@JoinColumn(name="payroll_run_item_id"),
 inverseJoinColumns=@JoinColumn(name="settlement_id")) private List<DriverSettlement> settlements=new ArrayList<>();
 @Version @Column(nullable=false) private Long version;
}
