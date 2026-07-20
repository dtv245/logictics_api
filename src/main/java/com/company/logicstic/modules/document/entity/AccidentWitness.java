package com.company.logicstic.modules.document.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "accident_witnesses",
    schema = "public",
    indexes = {
        @Index(name = "ix_accident_witnesses_accident_report_id", columnList = "accident_report_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AccidentWitness {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accident_report_id", nullable = false)
    private AccidentReport accidentReport;

    @Column(name = "\"name\"", nullable = false, length = 200)
    private String name;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "\"statement\"", length = 4000)
    private String statement;
}
