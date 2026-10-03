package com.company.logicstic.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.math.BigDecimal;
@Entity @Table(name="payroll_supplements") @Getter @Setter @NoArgsConstructor
public class PayrollSupplement {
 @Id @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payroll_run_item_id",nullable=false) private PayrollRunItem item;
 @Column(name="line_class",nullable=false,length=30) private String lineClass;
 @Column(nullable=false,length=300) private String description;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
}
