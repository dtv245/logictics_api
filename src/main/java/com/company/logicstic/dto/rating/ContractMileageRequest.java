package com.company.logicstic.dto.rating;

import com.company.logicstic.service.rating.domain.RatingMileageComponent;
import java.math.BigDecimal;
import java.util.UUID;

public record ContractMileageRequest(RatingMileageComponent componentType, UUID contractId, Integer contractVersion,
                                     BigDecimal originalValue, String originalUnit, String provenance) { }
