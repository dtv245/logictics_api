package com.company.logicstic.modules.fleet.entity;

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
    name = "posted_trucks",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_posted_trucks_truck_id_provider_type",
          columnNames = {"truck_id", "provider_type"})
    },
    indexes = {
      @Index(name = "ix_posted_trucks_external_post_id", columnList = "external_post_id"),
      @Index(name = "ix_posted_trucks_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
public class PostedTruck extends BaseAuditableEntity {

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

  @Column(name = "external_post_id", length = 100)
  private String externalPostId;

  @Column(name = "destination_radius")
  private Integer destinationRadius;

  @Column(name = "available_from", nullable = false)
  private OffsetDateTime availableFrom;

  @Column(name = "available_to")
  private OffsetDateTime availableTo;

  @Column(name = "equipment_type", length = 50)
  private String equipmentType;

  @Column(name = "max_weight")
  private Integer maxWeight;

  @Column(name = "max_length")
  private Integer maxLength;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "last_refreshed_at")
  private OffsetDateTime lastRefreshedAt;

  @Column(name = "available_at_address_city", nullable = false, columnDefinition = "text")
  private String availableAtAddressCity;

  @Column(name = "available_at_address_country", nullable = false, columnDefinition = "text")
  private String availableAtAddressCountry;

  @Column(name = "available_at_address_line1", nullable = false, columnDefinition = "text")
  private String availableAtAddressLine1;

  @Column(name = "available_at_address_line2", columnDefinition = "text")
  private String availableAtAddressLine2;

  @Column(name = "available_at_address_state", nullable = false, columnDefinition = "text")
  private String availableAtAddressState;

  @Column(name = "available_at_address_zip_code", nullable = false, columnDefinition = "text")
  private String availableAtAddressZipCode;

  @Column(name = "available_at_location_latitude", nullable = false)
  private Double availableAtLocationLatitude;

  @Column(name = "available_at_location_longitude", nullable = false)
  private Double availableAtLocationLongitude;

  @Column(name = "destination_preference_city", columnDefinition = "text")
  private String destinationPreferenceCity;

  @Column(name = "destination_preference_country", columnDefinition = "text")
  private String destinationPreferenceCountry;

  @Column(name = "destination_preference_line1", columnDefinition = "text")
  private String destinationPreferenceLine1;

  @Column(name = "destination_preference_line2", columnDefinition = "text")
  private String destinationPreferenceLine2;

  @Column(name = "destination_preference_state", columnDefinition = "text")
  private String destinationPreferenceState;

  @Column(name = "destination_preference_zip_code", columnDefinition = "text")
  private String destinationPreferenceZipCode;
}
