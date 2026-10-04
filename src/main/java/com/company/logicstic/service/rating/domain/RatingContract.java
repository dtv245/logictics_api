package com.company.logicstic.service.rating.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RatingContract(UUID contractId, int version, UUID customerId, String currency,
                             LocalDate effectiveFrom, LocalDate effectiveTo,
                             UUID createdBy, OffsetDateTime createdAt) { }
