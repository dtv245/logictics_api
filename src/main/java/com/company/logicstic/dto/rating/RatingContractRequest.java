package com.company.logicstic.dto.rating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record RatingContractRequest(
        @NotNull UUID customerId,
        @NotBlank String currency,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) { }
