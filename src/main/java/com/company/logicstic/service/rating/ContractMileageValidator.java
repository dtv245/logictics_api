package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.ContractMileageRequest;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.RatingMileageComponent;
import java.math.BigDecimal;

public final class ContractMileageValidator {
    private ContractMileageValidator() { }
    public static void validate(ContractMileageRequest r) {
        if (r == null || r.componentType() == null || r.componentType() == RatingMileageComponent.MINIMUM_CHARGE
                || r.contractId() == null || r.contractVersion() == null || r.contractVersion() <= 0
                || r.originalValue() == null || !"MILE".equals(r.originalUnit())
                || r.provenance() == null || r.provenance().isBlank())
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Explicit component/contract version/mile unit/value and agreement provenance required");
        // Keep V3 distance precision without pre-tier rounding or hidden unit conversion.
        if (r.originalValue().signum() < 0 || r.originalValue().compareTo(new BigDecimal("999999999.999")) > 0
                || r.originalValue().stripTrailingZeros().scale() > 3)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Mileage must be nonnegative and exactly representable at domain NUMERIC(12,3)");
    }
}
