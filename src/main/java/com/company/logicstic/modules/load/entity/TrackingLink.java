package com.company.logicstic.modules.load.entity;

import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "tracking_links",
    schema = "public",
    indexes = {
      @Index(name = "ix_tracking_links_expires_at", columnList = "expires_at"),
      @Index(name = "ix_tracking_links_load_id", columnList = "load_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class TrackingLink extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"token\"", nullable = false, unique = true, length = 128)
  private String token;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "load_id", nullable = false)
  private Load load;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "access_count", nullable = false)
  private Integer accessCount;

  @Column(name = "last_accessed_at")
  private OffsetDateTime lastAccessedAt;
}
