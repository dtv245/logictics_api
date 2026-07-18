package com.company.logicstic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

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
        @Index(name = "ix_loads_origin_terminal_id", columnList = "origin_terminal_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Load extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "\"number\"", nullable = false, insertable = false, updatable = false, unique = true)
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
}
