package com.company.logicstic.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "customers", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends BaseAuditableEntity {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"name\"", nullable = false, columnDefinition = "text")
  private String name;

  @Column(name = "email", columnDefinition = "text")
  private String email;

  @Column(name = "phone", columnDefinition = "text")
  private String phone;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @Column(name = "tax_id", length = 50)
  private String taxId;

  @Column(name = "is_vat_exempt", nullable = false)
  private Boolean isVatExempt;

  @Column(name = "address_city", columnDefinition = "text")
  private String addressCity;

  @Column(name = "address_country", columnDefinition = "text")
  private String addressCountry;

  @Column(name = "address_line1", columnDefinition = "text")
  private String addressLine1;

  @Column(name = "address_line2", columnDefinition = "text")
  private String addressLine2;

  @Column(name = "address_state", columnDefinition = "text")
  private String addressState;

  @Column(name = "address_zip_code", columnDefinition = "text")
  private String addressZipCode;
}
