package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record RatingLine(String componentType, UUID sourceId, String description,
        BigDecimal unroundedAmount, BigDecimal amount, String currency) { }
