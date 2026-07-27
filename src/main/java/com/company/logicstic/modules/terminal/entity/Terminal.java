package com.company.logicstic.modules.terminal.entity;

import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.shared.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "terminals", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Terminal extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"name\"", nullable = false, length = 200)
  private String name;

  @Column(name = "code", nullable = false, unique = true, length = 5)
  private String code;

  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @Column(name = "\"type\"", nullable = false, columnDefinition = "text")
  private String type;

  @Column(name = "notes", length = 2000)
  private String notes;

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

  // ── Typed access to the "type" text column ─────────────────────────

  /** Returns {@link #type} as a {@link TerminalType}. */
  public TerminalType getTypeEnum() {
    return TerminalType.fromDbValue(type);
  }

  /** Sets {@link #type} from a {@link TerminalType} (stores its database representation). */
  public void setTypeEnum(TerminalType terminalType) {
    this.type = terminalType == null ? null : terminalType.dbValue();
  }

  // ── Domain behaviour ──────────────────────────────────────────────

  /**
   * Normalises the identifying fields so lookups and the unique index agree on one representation:
   * UN/LOCODE and country code upper-cased, name trimmed.
   *
   * <p>Called on both create and update so the two paths cannot diverge.
   */
  public void normalise() {
    this.code = code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
    this.countryCode =
        countryCode == null ? null : countryCode.trim().toUpperCase(java.util.Locale.ROOT);
    this.name = name == null ? null : name.trim();
  }
}
