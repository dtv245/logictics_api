package com.company.logicstic.modules.employee.entity;

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
    name = "eld_driver_mappings",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_eld_driver_mappings_provider_type_employee_id",
          columnNames = {"provider_type", "employee_id"}),
      @UniqueConstraint(
          name = "ix_eld_driver_mappings_provider_type_external_driver_id",
          columnNames = {"provider_type", "external_driver_id"})
    },
    indexes = {@Index(name = "ix_eld_driver_mappings_employee_id", columnList = "employee_id")})
@Getter
@Setter
@NoArgsConstructor
public class EldDriverMapping {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "provider_type", nullable = false, columnDefinition = "text")
  private String providerType;

  @Column(name = "external_driver_id", nullable = false, length = 100)
  private String externalDriverId;

  @Column(name = "external_driver_name", length = 200)
  private String externalDriverName;

  @Column(name = "is_sync_enabled", nullable = false)
  private Boolean isSyncEnabled;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;
}
