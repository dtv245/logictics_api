package com.company.logicstic.modules.fleet.entity;

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
    name = "eld_vehicle_mappings",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_eld_vehicle_mappings_provider_type_external_vehicle_id",
          columnNames = {"provider_type", "external_vehicle_id"}),
      @UniqueConstraint(
          name = "ix_eld_vehicle_mappings_provider_type_truck_id",
          columnNames = {"provider_type", "truck_id"})
    },
    indexes = {@Index(name = "ix_eld_vehicle_mappings_truck_id", columnList = "truck_id")})
@Getter
@Setter
@NoArgsConstructor
public class EldVehicleMapping {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "truck_id", nullable = false)
  private Truck truck;

  @Column(name = "provider_type", nullable = false, columnDefinition = "text")
  private String providerType;

  @Column(name = "external_vehicle_id", nullable = false, length = 100)
  private String externalVehicleId;

  @Column(name = "external_vehicle_name", length = 200)
  private String externalVehicleName;

  @Column(name = "is_sync_enabled", nullable = false)
  private Boolean isSyncEnabled;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;
}
