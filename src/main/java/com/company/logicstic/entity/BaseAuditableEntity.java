package com.company.logicstic.entity;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseAuditableEntity {

    @CreatedDate
    @Column(name = "\"CreatedAt\"", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "\"CreatedBy\"", length = 50)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "\"LastModifiedAt\"")
    private OffsetDateTime lastModifiedAt;

    @LastModifiedBy
    @Column(name = "\"LastModifiedBy\"", length = 50)
    private String lastModifiedBy;
}
