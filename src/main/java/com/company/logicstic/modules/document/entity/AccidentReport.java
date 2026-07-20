package com.company.logicstic.modules.document.entity;

import com.company.logicstic.modules.trip.entity.Trip;
import com.company.logicstic.shared.BaseAuditableEntity;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.fleet.entity.Truck;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "accident_reports",
    schema = "public",
    indexes = {
        @Index(name = "ix_accident_reports_driver_id_accident_date_time", columnList = "driver_id,accident_date_time"),
        @Index(name = "ix_accident_reports_reviewed_by_id", columnList = "reviewed_by_id"),
        @Index(name = "ix_accident_reports_severity", columnList = "severity"),
        @Index(name = "ix_accident_reports_status", columnList = "status"),
        @Index(name = "ix_accident_reports_trip_id", columnList = "trip_id"),
        @Index(name = "ix_accident_reports_truck_id", columnList = "truck_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AccidentReport extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Employee driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "accident_type", nullable = false, columnDefinition = "text")
    private String accidentType;

    @Column(name = "severity", nullable = false, columnDefinition = "text")
    private String severity;

    @Column(name = "accident_date_time", nullable = false)
    private OffsetDateTime accidentDateTime;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "weather_conditions", length = 200)
    private String weatherConditions;

    @Column(name = "road_conditions", length = 200)
    private String roadConditions;

    @Column(name = "any_injuries", nullable = false)
    private Boolean anyInjuries;

    @Column(name = "number_of_injuries")
    private Integer numberOfInjuries;

    @Column(name = "injury_description", length = 2000)
    private String injuryDescription;

    @Column(name = "vehicle_damaged", nullable = false)
    private Boolean vehicleDamaged;

    @Column(name = "vehicle_damage_description", length = 2000)
    private String vehicleDamageDescription;

    @Column(name = "estimated_damage_cost", precision = 18, scale = 2)
    private BigDecimal estimatedDamageCost;

    @Column(name = "vehicle_drivable", nullable = false)
    private Boolean vehicleDrivable;

    @Column(name = "police_report_filed", nullable = false)
    private Boolean policeReportFiled;

    @Column(name = "police_report_number", length = 100)
    private String policeReportNumber;

    @Column(name = "police_officer_name", length = 200)
    private String policeOfficerName;

    @Column(name = "police_officer_badge", length = 50)
    private String policeOfficerBadge;

    @Column(name = "police_department", length = 200)
    private String policeDepartment;

    @Column(name = "insurance_notified", nullable = false)
    private Boolean insuranceNotified;

    @Column(name = "insurance_notified_at")
    private OffsetDateTime insuranceNotifiedAt;

    @Column(name = "insurance_claim_number", length = 100)
    private String insuranceClaimNumber;

    @Column(name = "driver_statement", length = 4000)
    private String driverStatement;

    @Column(name = "driver_signature", columnDefinition = "text")
    private String driverSignature;

    @Column(name = "driver_signed_at")
    private OffsetDateTime driverSignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Employee reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;

    @OneToMany(
        mappedBy = "accidentReport",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE},
        orphanRemoval = true
    )
    private List<AccidentThirdParty> thirdParties = new ArrayList<>();

    @OneToMany(
        mappedBy = "accidentReport",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE},
        orphanRemoval = true
    )
    private List<AccidentWitness> witnesses = new ArrayList<>();
}
