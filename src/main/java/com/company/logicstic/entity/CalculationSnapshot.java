package com.company.logicstic.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "calculation_snapshots",
    schema = "public",
    indexes = {
        @Index(name = "ix_calculation_snapshots_entity", columnList = "entity_type, entity_id"),
        @Index(name = "ix_calculation_snapshots_correlation", columnList = "correlation_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CalculationSnapshot {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "calculation_type", nullable = false, length = 60)
    private String calculationType;

    @Column(name = "engine_name", nullable = false, length = 100)
    private String engineName;

    @Column(name = "engine_version", nullable = false, length = 50)
    private String engineVersion;

    @Column(name = "policy_type", length = 80)
    private String policyType;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "policy_version", length = 50)
    private String policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false, columnDefinition = "jsonb")
    private String inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt = OffsetDateTime.now();

    @Column(name = "calculated_by")
    private UUID calculatedBy;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
