package com.company.logicstic.shared;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseAuditableEntity {

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @CreatedBy
  @Column(name = "created_by", length = 50)
  private String createdBy;

  @LastModifiedDate
  @Column(name = "last_modified_at")
  private OffsetDateTime lastModifiedAt;

  @LastModifiedBy
  @Column(name = "last_modified_by", length = 50)
  private String lastModifiedBy;
}
