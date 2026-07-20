package com.company.logicstic.modules.fleet.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "maintenance_parts",
    schema = "public",
    indexes = {
      @Index(
          name = "ix_maintenance_parts_maintenance_record_id",
          columnList = "maintenance_record_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class MaintenancePart {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "maintenance_record_id", nullable = false)
  private MaintenanceRecord maintenanceRecord;

  @Column(name = "part_name", nullable = false, length = 200)
  private String partName;

  @Column(name = "part_number", length = 100)
  private String partNumber;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "total_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalCost;
}
