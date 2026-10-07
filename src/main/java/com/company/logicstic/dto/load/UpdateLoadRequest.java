package com.company.logicstic.dto.load;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateLoadRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedVersion,
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
        
        @NotBlank String originAddressLine1,
        String originAddressLine2,
        @NotBlank String originAddressCity,
        @NotBlank String originAddressState,
        @NotBlank String originAddressZipCode,
        @NotBlank String originAddressCountry,
        @NotNull Double originLocationLatitude,
        @NotNull Double originLocationLongitude,
        
        @NotBlank String destinationAddressLine1,
        String destinationAddressLine2,
        @NotBlank String destinationAddressCity,
        @NotBlank String destinationAddressState,
        @NotBlank String destinationAddressZipCode,
        @NotBlank String destinationAddressCountry,
        @NotNull Double destinationLocationLatitude,
        @NotNull Double destinationLocationLongitude,
        LocalDate requestedPickupBusinessDate,
        @jakarta.validation.Valid PickupBusinessDateProvenance requestedPickupDateProvenance
) {
    public CreateLoadRequest toCreateRequest() {
        return new CreateLoadRequest(name, type, status, distance, isInProximity, customerId, assignedTruckId, assignedDispatcherId, source, requestedPickupDate, requestedDeliveryDate, notes, isHazmat, hazmatClass, unNumber, containerId, originTerminalId, destinationTerminalId, externalSourceProvider, externalSourceId, externalBrokerReference, deliveryCostAmount, deliveryCostCurrency, originAddressLine1, originAddressLine2, originAddressCity, originAddressState, originAddressZipCode, originAddressCountry, originLocationLatitude, originLocationLongitude, destinationAddressLine1, destinationAddressLine2, destinationAddressCity, destinationAddressState, destinationAddressZipCode, destinationAddressCountry, destinationLocationLatitude, destinationLocationLongitude, requestedPickupBusinessDate, requestedPickupDateProvenance);
    }
    public static UpdateLoadRequest from(CreateLoadRequest request, Long expectedVersion) {
        return new UpdateLoadRequest(expectedVersion, request.name(), request.type(), request.status(), request.distance(), request.isInProximity(), request.customerId(), request.assignedTruckId(), request.assignedDispatcherId(), request.source(), request.requestedPickupDate(), request.requestedDeliveryDate(), request.notes(), request.isHazmat(), request.hazmatClass(), request.unNumber(), request.containerId(), request.originTerminalId(), request.destinationTerminalId(), request.externalSourceProvider(), request.externalSourceId(), request.externalBrokerReference(), request.deliveryCostAmount(), request.deliveryCostCurrency(), request.originAddressLine1(), request.originAddressLine2(), request.originAddressCity(), request.originAddressState(), request.originAddressZipCode(), request.originAddressCountry(), request.originLocationLatitude(), request.originLocationLongitude(), request.destinationAddressLine1(), request.destinationAddressLine2(), request.destinationAddressCity(), request.destinationAddressState(), request.destinationAddressZipCode(), request.destinationAddressCountry(), request.destinationLocationLatitude(), request.destinationLocationLongitude(), request.requestedPickupBusinessDate(), request.requestedPickupDateProvenance());
    }
}
