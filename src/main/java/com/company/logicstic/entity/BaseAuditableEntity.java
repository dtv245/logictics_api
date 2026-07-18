package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseAuditableEntity {

    @CreationTimestamp
    @Column(name = "\"CreatedAt\"", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "\"CreatedBy\"", length = 50)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "\"LastModifiedAt\"")
    private OffsetDateTime lastModifiedAt;

    @Column(name = "\"LastModifiedBy\"", length = 50)
    private String lastModifiedBy;
}
