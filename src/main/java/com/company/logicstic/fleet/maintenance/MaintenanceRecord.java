package com.company.logicstic.fleet.maintenance;

import com.company.logicstic.employee.employee.Employee;
import com.company.logicstic.fleet.truck.Truck;
import com.company.logicstic.shared.persistence.BaseAuditableEntity;
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
    name = "maintenance_records",
    schema = "public",
    indexes = {
      @Index(
          name = "ix_maintenance_records_maintenance_schedule_id",
          columnList = "maintenance_schedule_id"),
      @Index(name = "ix_maintenance_records_maintenance_type", columnList = "maintenance_type"),
      @Index(name = "ix_maintenance_records_performed_by_id", columnList = "performed_by_id"),
      @Index(
          name = "ix_maintenance_records_truck_id_service_date",
          columnList = "truck_id,service_date")
    })
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceRecord extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "truck_id", nullable = false)
  private Truck truck;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "maintenance_schedule_id")
  private MaintenanceSchedule maintenanceSchedule;

  @Column(name = "maintenance_type", nullable = false, columnDefinition = "text")
  private String maintenanceType;

  @Column(name = "service_date", nullable = false)
  private OffsetDateTime serviceDate;

  @Column(name = "odometer_reading", nullable = false)
  private Integer odometerReading;

  @Column(name = "engine_hours")
  private Integer engineHours;

  @Column(name = "vendor_name", length = 200)
  private String vendorName;

  @Column(name = "vendor_address", length = 500)
  private String vendorAddress;

  @Column(name = "invoice_number", length = 100)
  private String invoiceNumber;

  @Column(name = "labor_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal laborCost;

  @Column(name = "parts_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal partsCost;

  @Column(name = "total_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalCost;

  /**
   * Currency of {@link #totalCost}, or {@code null} when it was never recorded.
   *
   * <p>Every other monetary column in this schema carries its currency beside it; this one never
   * did, which is why maintenance spend could not be added to anything. A null here means unknown,
   * so an aggregate excludes the row and counts the exclusion rather than assuming the requested
   * currency.
   */
  @Column(name = "total_cost_currency", length = 3)
  private String totalCostCurrency;

  /** When the vehicle went out of service, or {@code null} if that was never recorded. */
  @Column(name = "downtime_start_at")
  private OffsetDateTime downtimeStartAt;

  /**
   * When the vehicle returned to service, or {@code null} if it has not, or the end was never
   * recorded.
   */
  @Column(name = "downtime_end_at")
  private OffsetDateTime downtimeEndAt;

  /**
   * Whether this work was unplanned, or {@code null} when unclassified.
   *
   * <p>Boxed rather than primitive. A primitive {@code boolean} defaults to {@code false}, which
   * would silently classify every row written before this column existed as planned maintenance and
   * quietly deflate the unplanned-downtime figure. {@code null} makes "not known" a state the
   * aggregate has to handle explicitly, and it handles it by dropping the row and reporting the
   * count.
   */
  @Column(name = "is_unplanned")
  private Boolean isUnplanned;

  /**
   * Whether this record was a breakdown, or {@code null} when unclassified. Boxed for the same
   * reason as {@link #isUnplanned}: a default of {@code false} is an assertion, not a value.
   */
  @Column(name = "is_breakdown")
  private Boolean isBreakdown;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "work_performed", length = 2000)
  private String workPerformed;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "performed_by_id")
  private Employee performedBy;

  @OneToMany(
      mappedBy = "maintenanceRecord",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<MaintenancePart> parts = new ArrayList<>();
}
