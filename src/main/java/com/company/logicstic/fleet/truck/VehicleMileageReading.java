package com.company.logicstic.fleet.truck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One odometer reading for one truck at one moment.
 *
 * <p>Append-only. A reading is a fact about a moment and is never revised — a correction is a new
 * reading, not an edit — so this entity deliberately does not extend {@code BaseAuditableEntity}
 * and carries no {@code lastModified*} columns. {@link #recordedAt} is the only timestamp that
 * means anything here, and it is not nullable: an undated odometer reading cannot be differenced
 * against another one, so it cannot produce a distance, so it would be dead weight in the table.
 *
 * <p>Distance over a period is the difference between two readings, which is the reason this table
 * exists at all: a single {@code trucks.current_odometer} column gives a position, not a distance.
 *
 * <p>Nothing writes this table yet. It is created so that a telematics feed has somewhere to write
 * on the day one is connected; until then, the reporting metrics that need it report themselves as
 * unavailable rather than falling back to a number that would be invented.
 */
@Entity
@Table(name = "vehicle_mileage_readings", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class VehicleMileageReading {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "truck_id", nullable = false)
  private Truck truck;

  /** The odometer value observed, in the same unit and type as {@code trucks.current_odometer}. */
  @Column(name = "reading_value", nullable = false)
  private Integer readingValue;

  /** When the odometer showed {@link #readingValue}. Not the time the row was inserted. */
  @Column(name = "recorded_at", nullable = false)
  private OffsetDateTime recordedAt;

  /**
   * Where the reading came from — a provider key, or a manual entry marker. Free text rather than
   * an enum because no vocabulary has been agreed yet, and inventing one now would be the same
   * mistake as a {@code DEFAULT false}.
   */
  @Column(name = "source", length = 50)
  private String source;
}
