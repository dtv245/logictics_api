package com.company.logicstic.modules.load.entity;

import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "load_board_listings",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_load_board_listings_external_listing_id_provider_type",
          columnNames = {"external_listing_id", "provider_type"})
    },
    indexes = {
      @Index(name = "ix_load_board_listings_expires_at", columnList = "expires_at"),
      @Index(name = "ix_load_board_listings_load_id", columnList = "load_id"),
      @Index(name = "ix_load_board_listings_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
public class LoadBoardListing extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "external_listing_id", nullable = false, length = 100)
  private String externalListingId;

  @Column(name = "provider_type", nullable = false, columnDefinition = "text")
  private String providerType;

  @Column(name = "rate_per_mile", precision = 18, scale = 2)
  private BigDecimal ratePerMile;

  @Column(name = "distance")
  private Double distance;

  @Column(name = "weight")
  private Integer weight;

  @Column(name = "length")
  private Integer length;

  @Column(name = "pickup_date_start")
  private OffsetDateTime pickupDateStart;

  @Column(name = "pickup_date_end")
  private OffsetDateTime pickupDateEnd;

  @Column(name = "delivery_date_start")
  private OffsetDateTime deliveryDateStart;

  @Column(name = "delivery_date_end")
  private OffsetDateTime deliveryDateEnd;

  @Column(name = "equipment_type", length = 50)
  private String equipmentType;

  @Column(name = "commodity", length = 200)
  private String commodity;

  @Column(name = "broker_name", length = 200)
  private String brokerName;

  @Column(name = "broker_phone", length = 30)
  private String brokerPhone;

  @Column(name = "broker_email", length = 200)
  private String brokerEmail;

  @Column(name = "broker_mc_number", length = 20)
  private String brokerMcNumber;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "booked_at")
  private OffsetDateTime bookedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "load_id")
  private Load load;

  @Column(name = "notes", length = 2000)
  private String notes;

  @Column(name = "raw_json", columnDefinition = "text")
  private String rawJson;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "destination_address_city", nullable = false, columnDefinition = "text")
  private String destinationAddressCity;

  @Column(name = "destination_address_country", nullable = false, columnDefinition = "text")
  private String destinationAddressCountry;

  @Column(name = "destination_address_line1", nullable = false, columnDefinition = "text")
  private String destinationAddressLine1;

  @Column(name = "destination_address_line2", columnDefinition = "text")
  private String destinationAddressLine2;

  @Column(name = "destination_address_state", nullable = false, columnDefinition = "text")
  private String destinationAddressState;

  @Column(name = "destination_address_zip_code", nullable = false, columnDefinition = "text")
  private String destinationAddressZipCode;

  @Column(name = "destination_location_latitude", nullable = false)
  private Double destinationLocationLatitude;

  @Column(name = "destination_location_longitude", nullable = false)
  private Double destinationLocationLongitude;

  @Column(name = "origin_address_city", nullable = false, columnDefinition = "text")
  private String originAddressCity;

  @Column(name = "origin_address_country", nullable = false, columnDefinition = "text")
  private String originAddressCountry;

  @Column(name = "origin_address_line1", nullable = false, columnDefinition = "text")
  private String originAddressLine1;

  @Column(name = "origin_address_line2", columnDefinition = "text")
  private String originAddressLine2;

  @Column(name = "origin_address_state", nullable = false, columnDefinition = "text")
  private String originAddressState;

  @Column(name = "origin_address_zip_code", nullable = false, columnDefinition = "text")
  private String originAddressZipCode;

  @Column(name = "origin_location_latitude", nullable = false)
  private Double originLocationLatitude;

  @Column(name = "origin_location_longitude", nullable = false)
  private Double originLocationLongitude;

  @Column(name = "total_rate_amount", precision = 18, scale = 2)
  private BigDecimal totalRateAmount;

  @Column(name = "total_rate_currency", length = 3)
  private String totalRateCurrency;
}
