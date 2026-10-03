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

@Entity @Table(name="tenant_payroll_settings") @Getter @Setter @NoArgsConstructor
public class TenantPayrollSettings {
 @Id @Column(nullable=false,updatable=false) private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="default_jurisdiction_id") private PayrollJurisdictionEntity defaultJurisdiction;
}
