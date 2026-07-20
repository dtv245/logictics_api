package com.company.logicstic.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "trucks",
    schema = "public",
    indexes = {
      @Index(name = "ix_trucks_main_driver_id", columnList = "main_driver_id"),
      @Index(name = "ix_trucks_secondary_driver_id", columnList = "secondary_driver_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Truck {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"number\"", nullable = false, unique = true, columnDefinition = "text")
  private String number;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @Column(name = "vehicle_capacity", nullable = false)
  private Integer vehicleCapacity;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "make", columnDefinition = "text")
  private String make;

  @Column(name = "model", columnDefinition = "text")
  private String model;

  @Column(name = "\"year\"")
  private Integer year;

  @Column(name = "vin", columnDefinition = "text")
  private String vin;

  @Column(name = "license_plate", columnDefinition = "text")
  private String licensePlate;

  @Column(name = "license_plate_state", columnDefinition = "text")
  private String licensePlateState;

  @Column(name = "is_hazmat_placarded", nullable = false)
  private Boolean isHazmatPlacarded;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "main_driver_id")
  private Employee mainDriver;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "secondary_driver_id")
  private Employee secondaryDriver;

  @Column(name = "adr_equipment_adr_cert_expires_at")
  private OffsetDateTime adrEquipmentAdrCertExpiresAt;

  @Column(name = "adr_equipment_allowed_classes", nullable = false, columnDefinition = "text")
  private String adrEquipmentAllowedClasses;

  @Column(name = "adr_equipment_is_adr_certified", nullable = false)
  private Boolean adrEquipmentIsAdrCertified;

  @Column(name = "adr_equipment_orange_plate_number", length = 8)
  private String adrEquipmentOrangePlateNumber;

  @Column(name = "current_address_city", columnDefinition = "text")
  private String currentAddressCity;

  @Column(name = "current_address_country", columnDefinition = "text")
  private String currentAddressCountry;

  @Column(name = "current_address_line1", columnDefinition = "text")
  private String currentAddressLine1;

  @Column(name = "current_address_line2", columnDefinition = "text")
  private String currentAddressLine2;

  @Column(name = "current_address_state", columnDefinition = "text")
  private String currentAddressState;

  @Column(name = "current_address_zip_code", columnDefinition = "text")
  private String currentAddressZipCode;

  @Column(name = "current_location_latitude")
  private Double currentLocationLatitude;

  @Column(name = "current_location_longitude")
  private Double currentLocationLongitude;
}
