package com.company.logicstic.modules.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTripRequest(
        @NotBlank String name,
        @NotNull Double totalDistance,
        @NotBlank String status,
        UUID truckId
) {}
