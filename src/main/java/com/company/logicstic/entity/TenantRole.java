package com.company.logicstic.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "tenant_roles", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
