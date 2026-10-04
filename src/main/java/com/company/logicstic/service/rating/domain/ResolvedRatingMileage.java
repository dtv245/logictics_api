package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Consequential accepted snapshots must retain every field, not merely the eligibleMiles total. */
public record ResolvedRatingMileage(RatingMileageComponent componentType, String mileageBasis,
                                    String mileageSourceType, String sourceReference, Integer sourceVersion,
                                    BigDecimal originalValue, String originalUnit, BigDecimal eligibleMiles,
                                    UUID evidenceId, String provenance, UUID capturedBy, OffsetDateTime capturedAt) { }
