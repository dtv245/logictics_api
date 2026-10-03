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

@Entity @Table(name="payroll_policy_versions") @Getter @Setter @NoArgsConstructor
public class PayrollPolicyVersion {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @Column(name="policy_code",nullable=false,length=80) private String policyCode;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="jurisdiction_id",nullable=false) private PayrollJurisdictionEntity jurisdiction;
 @Enumerated(EnumType.STRING) @Column(name="worker_classification",nullable=false,length=30)
 private com.company.logicstic.service.payroll.domain.WorkerClassification workerClassification;
 @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom;
 @Column(name="effective_to") private LocalDate effectiveTo;
 @Column(name="policy_version",nullable=false) private Integer policyVersion;
 @Column(nullable=false) private Boolean active;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="calculator_key",nullable=false,length=100) private String calculatorKey;
 @Column(name="authoritative_source",nullable=false,length=1000) private String authoritativeSource;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="configuration_json",nullable=false,columnDefinition="jsonb") private String configurationJson;
 public com.company.logicstic.service.payroll.domain.PayrollPolicy toDomain() {
 return new com.company.logicstic.service.payroll.domain.PayrollPolicy(id,policyCode,jurisdiction.toDomain(),workerClassification,
 effectiveFrom,effectiveTo,policyVersion,active,currency,calculatorKey,authoritativeSource,configurationJson);
 }
}
