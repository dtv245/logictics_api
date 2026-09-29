package com.company.logicstic.dto.trip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTripRequest(
        @NotBlank String name,
        @NotNull Double totalDistance,
        @NotBlank String status,
        UUID truckId
) {}
