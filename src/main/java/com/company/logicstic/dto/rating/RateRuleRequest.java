package com.company.logicstic.dto.rating;

import com.company.logicstic.service.rating.domain.IndexBasedFscPolicy;
import com.company.logicstic.service.rating.domain.RatingMethod;
import com.company.logicstic.service.rating.domain.RatingMileageBasis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RateRuleRequest(
        @NotNull Integer priority,
        UUID customerId,
        UUID contractId,
        Integer contractVersion,
        String lane,
        String equipment,
        String service,
        String tier,
        @NotBlank String currency,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotNull RatingMethod method,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal baseRate,
        RatingMileageBasis linehaulMileageBasis,
        BigDecimal minimumCharge,
        BigDecimal maximumCharge,
        @jakarta.validation.Valid IndexBasedFscPolicy fsc
) { }
