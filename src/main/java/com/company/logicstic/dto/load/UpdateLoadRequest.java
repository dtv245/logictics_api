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

public record UpdateLoadRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedVersion,
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
) {
    public CreateLoadRequest toCreateRequest() {
        return new CreateLoadRequest(name, type, status, distance, isInProximity, customerId, assignedTruckId, assignedDispatcherId, source, requestedPickupDate, requestedDeliveryDate, notes, isHazmat, hazmatClass, unNumber, containerId, originTerminalId, destinationTerminalId, externalSourceProvider, externalSourceId, externalBrokerReference, deliveryCostAmount, deliveryCostCurrency, originAddressLine1, originAddressLine2, originAddressCity, originAddressState, originAddressZipCode, originAddressCountry, originLocationLatitude, originLocationLongitude, destinationAddressLine1, destinationAddressLine2, destinationAddressCity, destinationAddressState, destinationAddressZipCode, destinationAddressCountry, destinationLocationLatitude, destinationLocationLongitude, requestedPickupBusinessDate, requestedPickupDateProvenance, weightLbs, volumeCuft);
    }
    public static UpdateLoadRequest from(CreateLoadRequest request, Long expectedVersion) {
        return new UpdateLoadRequest(expectedVersion, request.name(), request.type(), request.status(), request.distance(), request.isInProximity(), request.customerId(), request.assignedTruckId(), request.assignedDispatcherId(), request.source(), request.requestedPickupDate(), request.requestedDeliveryDate(), request.notes(), request.isHazmat(), request.hazmatClass(), request.unNumber(), request.containerId(), request.originTerminalId(), request.destinationTerminalId(), request.externalSourceProvider(), request.externalSourceId(), request.externalBrokerReference(), request.deliveryCostAmount(), request.deliveryCostCurrency(), request.originAddressLine1(), request.originAddressLine2(), request.originAddressCity(), request.originAddressState(), request.originAddressZipCode(), request.originAddressCountry(), request.originLocationLatitude(), request.originLocationLongitude(), request.destinationAddressLine1(), request.destinationAddressLine2(), request.destinationAddressCity(), request.destinationAddressState(), request.destinationAddressZipCode(), request.destinationAddressCountry(), request.destinationLocationLatitude(), request.destinationLocationLongitude(), request.requestedPickupBusinessDate(), request.requestedPickupDateProvenance(), request.weightLbs(), request.volumeCuft());
    }
}
