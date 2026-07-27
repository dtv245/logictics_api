package com.company.logicstic.modules.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "accident_third_parties",
    schema = "public",
    indexes = {
      @Index(
          name = "ix_accident_third_parties_accident_report_id",
          columnList = "accident_report_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class AccidentThirdParty {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "accident_report_id", nullable = false)
  private AccidentReport accidentReport;

  @Column(name = "\"name\"", nullable = false, length = 200)
  private String name;

  @Column(name = "phone_number", length = 50)
  private String phoneNumber;

  @Column(name = "address", length = 500)
  private String address;

  @Column(name = "driver_license", length = 50)
  private String driverLicense;

  @Column(name = "vehicle_make", length = 100)
  private String vehicleMake;

  @Column(name = "vehicle_model", length = 100)
  private String vehicleModel;

  @Column(name = "vehicle_year")
  private Integer vehicleYear;

  @Column(name = "vehicle_license_plate", length = 20)
  private String vehicleLicensePlate;

  @Column(name = "vehicle_vin", length = 20)
  private String vehicleVin;

  @Column(name = "vehicle_color", length = 50)
  private String vehicleColor;

  @Column(name = "insurance_company", length = 200)
  private String insuranceCompany;

  @Column(name = "insurance_policy_number", length = 100)
  private String insurancePolicyNumber;

  @Column(name = "insurance_agent_phone", length = 50)
  private String insuranceAgentPhone;
}
