package com.company.logicstic.modules.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateTripRequest(
    @NotBlank String name,
    @NotNull Double totalDistance,
    @NotBlank String status,
    UUID truckId,
    @NotNull @Valid List<TripStopRequest> stops) {}
