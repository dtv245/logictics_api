package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

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

    /** WGS84 latitude; null when the terminal has not been geocoded. */
    @Column(name = "latitude")
    private Double latitude;

    /** WGS84 longitude; null when the terminal has not been geocoded. */
    @Column(name = "longitude")
    private Double longitude;

    /** Local opening time; null when working hours are unknown. */
    @Column(name = "working_hours_open")
    private LocalTime workingHoursOpen;

    /** Local closing time; may be earlier than the opening time for overnight shifts. */
    @Column(name = "working_hours_close")
    private LocalTime workingHoursClose;
}
