package com.company.logicstic.dto.trip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateTripRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedVersion,
        @NotBlank String name,
        @NotNull Double totalDistance,
        @NotBlank String status,
        UUID truckId
) {
    public CreateTripRequest toCreateRequest() {
        return new CreateTripRequest(name, totalDistance, status, truckId);
    }
    public static UpdateTripRequest from(CreateTripRequest request, Long expectedVersion) {
        return new UpdateTripRequest(expectedVersion, request.name(), request.totalDistance(), request.status(), request.truckId());
    }
}
