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

@Entity @Table(name="payroll_jurisdictions") @Getter @Setter @NoArgsConstructor
public class PayrollJurisdictionEntity {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @Column(name="country_code",nullable=false,length=2) private String countryCode;
 @Column(name="subdivision_code",length=80) private String subdivisionCode;
 @Column(name="locality_code",length=120) private String localityCode;
 public com.company.logicstic.service.payroll.domain.PayrollJurisdiction toDomain() {
 return new com.company.logicstic.service.payroll.domain.PayrollJurisdiction(countryCode,subdivisionCode,localityCode);
 }
}
