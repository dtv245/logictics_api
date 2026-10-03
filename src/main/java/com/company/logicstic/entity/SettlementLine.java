package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="settlement_lines") @Getter @Setter @NoArgsConstructor
public class SettlementLine {
    @Id @GeneratedValue @UuidGenerator @Column(nullable=false,updatable=false) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="settlement_id",nullable=false) private DriverSettlement settlement;
    @Column(name="line_type",nullable=false,length=50) private String lineType;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="load_id") private Load load;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="trip_id") private Trip trip;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="accessorial_charge_id") private AccessorialCharge accessorialCharge;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="expense_id") private Expense expense;
    @Column(nullable=false,length=300) private String description;
    @Column(precision=19,scale=6) private BigDecimal quantity;
    @Column(length=30) private String unit;
    @Column(precision=19,scale=6) private BigDecimal rate;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false) private Boolean taxable=true;
    @Column(name="line_class",nullable=false,length=30) private String lineClass;
    @Column(name="source_type",length=40) private String sourceType;
    @Column(name="source_id") private UUID sourceId;
    @Column(name="business_date") private java.time.LocalDate businessDate;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="calculation_snapshot_id") private CalculationSnapshot calculationSnapshot;
}
