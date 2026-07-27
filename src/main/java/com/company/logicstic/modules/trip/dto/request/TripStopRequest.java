package com.company.logicstic.modules.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record TripStopRequest(
    @NotBlank String type,
    @NotNull @PositiveOrZero Integer order,
    @NotNull UUID loadId,
    @NotBlank String addressLine1,
    String addressLine2,
    @NotBlank String addressCity,
    @NotBlank String addressState,
    @NotBlank String addressZipCode,
    @NotBlank String addressCountry,
    @NotNull Double locationLatitude,
    @NotNull Double locationLongitude) {}
