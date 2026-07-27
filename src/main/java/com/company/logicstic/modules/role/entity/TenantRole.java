package com.company.logicstic.modules.role.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "tenant_roles", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class TenantRole {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"name\"", nullable = false, columnDefinition = "text")
  private String name;

  @Column(name = "display_name", columnDefinition = "text")
  private String displayName;

  @Column(name = "normalized_name", nullable = false, columnDefinition = "text")
  private String normalizedName;

  @OneToMany(
      mappedBy = "role",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<TenantRoleClaim> claims = new ArrayList<>();
}
