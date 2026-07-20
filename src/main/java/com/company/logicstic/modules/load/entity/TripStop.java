package com.company.logicstic.modules.load.entity;

import com.company.logicstic.modules.trip.entity.Trip;
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
    name = "trip_stops",
    schema = "public",
    indexes = {
      @Index(name = "ix_trip_stops_load_id", columnList = "load_id"),
      @Index(name = "ix_trip_stops_trip_id", columnList = "trip_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class TripStop {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Column(name = "\"order\"", nullable = false)
  private Integer order;

  @Column(name = "arrived_at")
  private OffsetDateTime arrivedAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "load_id", nullable = false)
  private Load load;

  @Column(name = "address_city", nullable = false, columnDefinition = "text")
  private String addressCity;

  @Column(name = "address_country", nullable = false, columnDefinition = "text")
  private String addressCountry;

  @Column(name = "address_line1", nullable = false, columnDefinition = "text")
  private String addressLine1;

  @Column(name = "address_line2", columnDefinition = "text")
  private String addressLine2;

  @Column(name = "address_state", nullable = false, columnDefinition = "text")
  private String addressState;

  @Column(name = "address_zip_code", nullable = false, columnDefinition = "text")
  private String addressZipCode;

  @Column(name = "location_latitude", nullable = false)
  private Double locationLatitude;

  @Column(name = "location_longitude", nullable = false)
  private Double locationLongitude;
}
