package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContractMileageEvidence(UUID id, UUID loadId, RatingMileageComponent componentType,
                                      UUID contractId, int contractVersion, String currency,
                                      BigDecimal originalValue, String originalUnit, BigDecimal normalizedMiles,
                                      String provenance, UUID capturedBy, OffsetDateTime capturedAt) { }
