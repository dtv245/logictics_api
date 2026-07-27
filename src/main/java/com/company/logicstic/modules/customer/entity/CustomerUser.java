package com.company.logicstic.modules.customer.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "customer_users",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_customer_users_customer_id_user_id",
          columnNames = {"customer_id", "user_id"})
    },
    indexes = {
      @Index(name = "ix_customer_users_email", columnList = "email"),
      @Index(name = "ix_customer_users_user_id", columnList = "user_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class CustomerUser extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Column(name = "email", nullable = false, length = 256)
  private String email;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive;

  @Column(name = "last_login_at")
  private OffsetDateTime lastLoginAt;

  @Column(name = "display_name", length = 256)
  private String displayName;
}
