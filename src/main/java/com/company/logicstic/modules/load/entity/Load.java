package com.company.logicstic.modules.load.entity;

import com.company.logicstic.modules.customer.entity.Customer;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.fleet.entity.Container;
import com.company.logicstic.modules.fleet.entity.Terminal;
import com.company.logicstic.modules.fleet.entity.Truck;
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
import java.math.BigDecimal;
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
    name = "loads",
    schema = "public",
    indexes = {
      @Index(name = "ix_loads_assigned_dispatcher_id", columnList = "assigned_dispatcher_id"),
      @Index(name = "ix_loads_assigned_truck_id", columnList = "assigned_truck_id"),
      @Index(name = "ix_loads_container_id", columnList = "container_id"),
      @Index(name = "ix_loads_customer_id", columnList = "customer_id"),
      @Index(name = "ix_loads_destination_terminal_id", columnList = "destination_terminal_id"),
      @Index(name = "ix_loads_origin_terminal_id", columnList = "origin_terminal_id"),
      @Index(name = "ix_loads_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
public class Load extends BaseAuditableEntity {

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

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "distance", nullable = false)
  private Double distance;

  @Column(name = "is_in_proximity", nullable = false)
  private Boolean isInProximity;

  @Column(name = "dispatched_at")
  private OffsetDateTime dispatchedAt;

  @Column(name = "picked_up_at")
  private OffsetDateTime pickedUpAt;

  @Column(name = "delivered_at")
  private OffsetDateTime deliveredAt;

  @Column(name = "cancelled_at")
  private OffsetDateTime cancelledAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_truck_id")
  private Truck assignedTruck;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_dispatcher_id")
  private Employee assignedDispatcher;

  @Column(name = "\"source\"", nullable = false, columnDefinition = "text")
  private String source;

  @Column(name = "requested_pickup_date")
  private OffsetDateTime requestedPickupDate;

  @Column(name = "requested_delivery_date")
  private OffsetDateTime requestedDeliveryDate;

  @Column(name = "notes", length = 2000)
  private String notes;

  @Column(name = "is_hazmat", nullable = false)
  private Boolean isHazmat;

  @Column(name = "hazmat_class", columnDefinition = "text")
  private String hazmatClass;

  @Column(name = "un_number", length = 16)
  private String unNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "container_id")
  private Container container;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "origin_terminal_id")
  private Terminal originTerminal;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "destination_terminal_id")
  private Terminal destinationTerminal;

  @Column(name = "external_source_provider", columnDefinition = "text")
  private String externalSourceProvider;

  @Column(name = "external_source_id", length = 100)
  private String externalSourceId;

  @Column(name = "external_broker_reference", length = 100)
  private String externalBrokerReference;

  @Column(name = "delivery_cost_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal deliveryCostAmount;

  @Column(name = "delivery_cost_currency", nullable = false, length = 3)
  private String deliveryCostCurrency;

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

  @OneToMany(
      mappedBy = "load",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<TrackingLink> trackingLinks = new ArrayList<>();

  @OneToMany(
      mappedBy = "load",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<LoadException> exceptions = new ArrayList<>();

  // ── Domain behavior: State machine ─────────────────────────────────

  /** Returns the current status as a {@link LoadStatus} enum. */
  public LoadStatus getStatusEnum() {
    return LoadStatus.fromDbValue(status);
  }

  /** Sets the status from a {@link LoadStatus} enum (stores its lowercase db value). */
  public void setStatusEnum(LoadStatus status) {
    this.status = status.dbValue();
  }

  /**
   * Transitions the Load to {@link LoadStatus#DISPATCHED}. Valid only if current state is DRAFT.
   * Sets {@code dispatchedAt} timestamp if null.
   *
   * @throws InvalidStateTransitionException if transition is not allowed
   */
  public void dispatch() {
    var current = getStatusEnum();
    if (!LoadStatus.isValidTransition(current, LoadStatus.DISPATCHED)) {
      throw new InvalidStateTransitionException(
          "Load", current != null ? current.dbValue() : "null", LoadStatus.DISPATCHED.dbValue());
    }
    setStatusEnum(LoadStatus.DISPATCHED);
    if (dispatchedAt == null) {
      dispatchedAt = OffsetDateTime.now();
    }
    // Reset downstream timestamps on re-dispatch
    pickedUpAt = null;
    deliveredAt = null;
    cancelledAt = null;
  }

  /**
   * Transitions the Load to {@link LoadStatus#PICKED_UP}. Valid only if current state is
   * DISPATCHED. Sets {@code pickedUpAt} timestamp if null.
   *
   * @throws InvalidStateTransitionException if transition is not allowed
   */
  public void pickUp() {
    var current = getStatusEnum();
    if (!LoadStatus.isValidTransition(current, LoadStatus.PICKED_UP)) {
      throw new InvalidStateTransitionException(
          "Load", current != null ? current.dbValue() : "null", LoadStatus.PICKED_UP.dbValue());
    }
    setStatusEnum(LoadStatus.PICKED_UP);
    if (pickedUpAt == null) {
      pickedUpAt = OffsetDateTime.now();
    }
  }

  /**
   * Transitions the Load to {@link LoadStatus#DELIVERED}. Valid only if current state is PICKED_UP.
   * Sets {@code deliveredAt} timestamp if null.
   *
   * @throws InvalidStateTransitionException if transition is not allowed
   */
  public void deliver() {
    var current = getStatusEnum();
    if (!LoadStatus.isValidTransition(current, LoadStatus.DELIVERED)) {
      throw new InvalidStateTransitionException(
          "Load", current != null ? current.dbValue() : "null", LoadStatus.DELIVERED.dbValue());
    }
    setStatusEnum(LoadStatus.DELIVERED);
    if (deliveredAt == null) {
      deliveredAt = OffsetDateTime.now();
    }
  }

  /**
   * Transitions the Load to {@link LoadStatus#CANCELLED} from any non-terminal state. Sets {@code
   * cancelledAt} timestamp if null.
   *
   * @throws InvalidStateTransitionException if the Load is already in a terminal state (DELIVERED
   *     or CANCELLED)
   */
  public void cancel() {
    var current = getStatusEnum();
    if (!LoadStatus.isValidTransition(current, LoadStatus.CANCELLED)) {
      throw new InvalidStateTransitionException(
          "Load", current != null ? current.dbValue() : "null", LoadStatus.CANCELLED.dbValue());
    }
    setStatusEnum(LoadStatus.CANCELLED);
    if (cancelledAt == null) {
      cancelledAt = OffsetDateTime.now();
    }
  }

  // ── Domain behavior: Driver Share calculation ───────────────────────

  /**
   * Calculates the driver share for this Load. Ratio = sum of salaryAmount of assigned truck's
   * mainDriver and secondaryDriver where salaryType = "ShareOfGross" DriverShare =
   * deliveryCostAmount × ratio
   *
   * <p>If ratio > 1, throws IllegalStateException (data integrity violation). If no ShareOfGross
   * driver, ratio = 0 and driverShare = 0.
   *
   * @return calculated driver share amount
   * @throws IllegalStateException if ratio exceeds 1.0 (invalid salary configuration)
   */
  public BigDecimal calcDriverShare() {
    if (assignedTruck == null) {
      return BigDecimal.ZERO;
    }

    BigDecimal ratio = BigDecimal.ZERO;

    if (assignedTruck.getMainDriver() != null
        && "ShareOfGross".equals(assignedTruck.getMainDriver().getSalaryType())) {
      ratio = ratio.add(assignedTruck.getMainDriver().getSalaryAmount());
    }
    if (assignedTruck.getSecondaryDriver() != null
        && "ShareOfGross".equals(assignedTruck.getSecondaryDriver().getSalaryType())) {
      ratio = ratio.add(assignedTruck.getSecondaryDriver().getSalaryAmount());
    }

    // Per spec: ratio must be between 0 and 1 (inclusive)
    if (ratio.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalStateException(
          "Driver share ratio "
              + ratio
              + " exceeds 1.0 for Load "
              + id
              + ". Check salary configuration of drivers assigned to truck "
              + assignedTruck.getId());
    }

    BigDecimal share = deliveryCostAmount.multiply(ratio);
    if (share.signum() == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal withoutInsignificantZeros = share.stripTrailingZeros();
    int scale = Math.max(deliveryCostAmount.scale(), withoutInsignificantZeros.scale());
    return withoutInsignificantZeros.setScale(Math.max(scale, 0));
  }
}
