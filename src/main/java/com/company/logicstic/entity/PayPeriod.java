package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pay_periods")
@Getter @Setter @NoArgsConstructor
public class PayPeriod {
    @Id @GeneratedValue @UuidGenerator @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "period_code", nullable = false, unique = true, length = 50)
    private String periodCode;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "payment_date")
    private LocalDate paymentDate;
    @Column(nullable = false, length = 30)
    private String status = "OPEN";
}
