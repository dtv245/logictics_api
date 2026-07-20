package com.company.logicstic.modules.load.entity;

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
    name = "condition_defects",
    schema = "public",
    indexes = {
        @Index(name = "ix_condition_defects_load_condition_report_id", columnList = "load_condition_report_id"),
        @Index(name = "ix_condition_defects_part_category", columnList = "part_category")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ConditionDefect {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "load_condition_report_id", nullable = false)
    private LoadConditionReport loadConditionReport;

    @Column(name = "part_category", nullable = false, length = 64)
    private String partCategory;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;
}
