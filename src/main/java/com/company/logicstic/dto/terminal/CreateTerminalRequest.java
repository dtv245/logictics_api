package com.company.logicstic.dto.terminal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record CreateTerminalRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @NotBlank(message = "Code is required")
        @Size(max = 5, message = "Code must be at most 5 characters")
        String code,

        @NotBlank(message = "Country code is required")
        @Size(max = 2, message = "Country code must be at most 2 characters")
        String countryCode,

        @NotBlank(message = "Type is required")
        String type,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        @NotBlank(message = "City is required")
        String addressCity,

        @NotBlank(message = "Country is required")
        String addressCountry,

        @NotBlank(message = "Address line 1 is required")
        String addressLine1,

        String addressLine2,

        @NotBlank(message = "State is required")
        String addressState,

        @NotBlank(message = "ZIP code is required")
        String addressZipCode,

        @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
        Double longitude,

        LocalTime workingHoursOpen,
        LocalTime workingHoursClose
) {}
