package com.company.logicstic.dto.load;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLoadRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String status,
        @NotNull @Positive(message = "Distance must be greater than 0") Double distance,
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
        @NotNull @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
        Double originLocationLatitude,
        @NotNull @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
        Double originLocationLongitude,
        // Destination address
        @NotBlank String destinationAddressLine1,
        String destinationAddressLine2,
        @NotBlank String destinationAddressCity,
        @NotBlank String destinationAddressState,
        @NotBlank String destinationAddressZipCode,
        @NotBlank String destinationAddressCountry,
        @NotNull @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
        Double destinationLocationLatitude,
        @NotNull @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
        Double destinationLocationLongitude,
        LocalDate requestedPickupBusinessDate,
        @jakarta.validation.Valid PickupBusinessDateProvenance requestedPickupDateProvenance,
        // Cargo
        @Positive(message = "Weight must be greater than 0") Double weightLbs,
        @Positive(message = "Volume must be greater than 0") Double volumeCuft
) {}
