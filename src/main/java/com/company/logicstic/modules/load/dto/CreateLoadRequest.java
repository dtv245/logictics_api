package com.company.logicstic.modules.load.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateLoadRequest(
    @NotBlank String name,
    @NotBlank String type,
    @NotBlank String status,
    @NotNull Double distance,
    @NotNull Boolean isInProximity,
    @NotNull UUID customerId,
    UUID assignedTruckId,
    UUID assignedDispatcherId,
    @NotBlank String source,
    OffsetDateTime requestedPickupDate,
    OffsetDateTime requestedDeliveryDate,
    String notes,
    @NotNull Boolean isHazmat,
    String hazmatClass,
    String unNumber,
    UUID containerId,
    UUID originTerminalId,
    UUID destinationTerminalId,
    String externalSourceProvider,
    String externalSourceId,
    String externalBrokerReference,
    @NotNull BigDecimal deliveryCostAmount,
    @NotBlank String deliveryCostCurrency,
    // Origin address
    @NotBlank String originAddressLine1,
    String originAddressLine2,
    @NotBlank String originAddressCity,
    @NotBlank String originAddressState,
    @NotBlank String originAddressZipCode,
    @NotBlank String originAddressCountry,
    @NotNull Double originLocationLatitude,
    @NotNull Double originLocationLongitude,
    // Destination address
    @NotBlank String destinationAddressLine1,
    String destinationAddressLine2,
    @NotBlank String destinationAddressCity,
    @NotBlank String destinationAddressState,
    @NotBlank String destinationAddressZipCode,
    @NotBlank String destinationAddressCountry,
    @NotNull Double destinationLocationLatitude,
    @NotNull Double destinationLocationLongitude) {}
