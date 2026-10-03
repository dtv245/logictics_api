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

@Entity @Table(name="employee_payroll_profiles") @Getter @Setter @NoArgsConstructor
public class EmployeePayrollProfile {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="employee_id",nullable=false) private Employee employee;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="jurisdiction_id") private PayrollJurisdictionEntity jurisdiction;
 @Enumerated(EnumType.STRING) @Column(name="worker_classification",nullable=false,length=30)
 private com.company.logicstic.service.payroll.domain.WorkerClassification workerClassification;
 @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom;
 @Column(name="effective_to") private LocalDate effectiveTo;
 @Column(name="profile_version",nullable=false) private Integer profileVersion;
 @Column(nullable=false) private Boolean active;
}
