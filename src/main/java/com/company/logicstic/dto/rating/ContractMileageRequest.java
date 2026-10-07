package com.company.logicstic.dto.rating;

import com.company.logicstic.service.rating.domain.RatingMileageComponent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ContractMileageRequest(
        @NotNull RatingMileageComponent componentType,
        UUID contractId,
        Integer contractVersion,
        @NotNull BigDecimal originalValue,
        @NotBlank String originalUnit,
        @NotBlank String provenance
) { }
