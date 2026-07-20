package com.company.logicstic.modules.employee.entity;

import com.company.logicstic.modules.finance.entity.Invoice;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "time_entries",
    schema = "public",
    indexes = {
      @Index(name = "ix_time_entries_employee_id_date", columnList = "employee_id,date"),
      @Index(name = "ix_time_entries_payroll_invoice_id", columnList = "payroll_invoice_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class TimeEntry extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "\"date\"", nullable = false)
  private OffsetDateTime date;

  @Column(name = "start_time", nullable = false, columnDefinition = "interval")
  private Duration startTime;

  @Column(name = "end_time", nullable = false, columnDefinition = "interval")
  private Duration endTime;

  @Column(name = "total_hours", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalHours;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payroll_invoice_id")
  private Invoice payrollInvoice;

  @Column(name = "notes", length = 500)
  private String notes;
}
