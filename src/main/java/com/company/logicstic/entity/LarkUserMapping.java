package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "lark_user_mappings",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_lark_user_mappings_open_id", columnNames = {"open_id"}),
        @UniqueConstraint(name = "uq_lark_user_mappings_union_id", columnNames = {"union_id"})
    },
    indexes = {
        @Index(name = "ix_lark_user_mappings_employee_id", columnList = "employee_id"),
        @Index(name = "ix_lark_user_mappings_email", columnList = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class LarkUserMapping {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "open_id", length = 128)
    private String openId;

    @Column(name = "union_id", length = 128)
    private String unionId;

    @Column(name = "lark_user_id", length = 128)
    private String larkUserId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
