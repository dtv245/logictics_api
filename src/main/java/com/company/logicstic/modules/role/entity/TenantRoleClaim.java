package com.company.logicstic.modules.role.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "tenant_role_claims",
    schema = "public",
    indexes = {@Index(name = "ix_tenant_role_claims_role_id", columnList = "role_id")})
@Getter
@Setter
@NoArgsConstructor
public class TenantRoleClaim {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "claim_type", nullable = false, columnDefinition = "text")
  private String claimType;

  @Column(name = "claim_value", nullable = false, columnDefinition = "text")
  private String claimValue;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private TenantRole role;
}
