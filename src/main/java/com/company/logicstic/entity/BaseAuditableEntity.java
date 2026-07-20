package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseAuditableEntity {

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "created_by", length = 50)
  private String createdBy;

  @UpdateTimestamp
  @Column(name = "last_modified_at")
  private OffsetDateTime lastModifiedAt;

  @Column(name = "last_modified_by", length = 50)
  private String lastModifiedBy;
}
