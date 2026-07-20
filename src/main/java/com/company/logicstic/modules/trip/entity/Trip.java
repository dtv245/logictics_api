package com.company.logicstic.modules.trip.entity;

import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.load.entity.TripStop;
import com.company.logicstic.shared.BaseAuditableEntity;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "trips",
    schema = "public",
    indexes = {@Index(name = "ix_trips_truck_id", columnList = "truck_id")})
@Getter
@Setter
@NoArgsConstructor
public class Trip extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(
      name = "\"number\"",
      nullable = false,
      insertable = false,
      updatable = false,
      unique = true)
  private Long number;

  @Column(name = "\"name\"", nullable = false, columnDefinition = "text")
  private String name;

  @Column(name = "total_distance", nullable = false)
  private Double totalDistance;

  @Column(name = "dispatched_at")
  private OffsetDateTime dispatchedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "cancelled_at")
  private OffsetDateTime cancelledAt;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "truck_id")
  private Truck truck;

  @OneToMany(
      mappedBy = "trip",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<TripStop> stops = new ArrayList<>();

  public TripStatus getStatusEnum() {
    return TripStatus.fromDbValue(status);
  }

  public void setStatusEnum(TripStatus status) {
    this.status = status.dbValue();
  }

  public void dispatch() {
    requireTransition(TripStatus.DRAFT, TripStatus.DISPATCHED);
    setStatusEnum(TripStatus.DISPATCHED);
    if (dispatchedAt == null) {
      dispatchedAt = OffsetDateTime.now();
    }
  }

  public void complete() {
    requireTransition(TripStatus.DISPATCHED, TripStatus.COMPLETED);
    setStatusEnum(TripStatus.COMPLETED);
    if (completedAt == null) {
      completedAt = OffsetDateTime.now();
    }
  }

  public void cancel() {
    TripStatus current = getStatusEnum();
    if (current == TripStatus.COMPLETED || current == TripStatus.CANCELLED) {
      throw invalidTransition(TripStatus.CANCELLED);
    }
    setStatusEnum(TripStatus.CANCELLED);
    if (cancelledAt == null) {
      cancelledAt = OffsetDateTime.now();
    }
  }

  private void requireTransition(TripStatus expected, TripStatus target) {
    if (getStatusEnum() != expected) {
      throw invalidTransition(target);
    }
  }

  private InvalidStateTransitionException invalidTransition(TripStatus target) {
    TripStatus current = getStatusEnum();
    return new InvalidStateTransitionException(
        "Trip", current != null ? current.dbValue() : "null", target.dbValue());
  }
}
