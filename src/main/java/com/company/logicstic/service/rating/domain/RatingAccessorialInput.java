package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RatingAccessorialInput(UUID chargeId, UUID loadId, String type, String status,
        BigDecimal quantity, String unit, BigDecimal rate, BigDecimal customerAmount, String currency,
        UUID approvedBy, OffsetDateTime approvedAt, Long sourceVersion) { }
