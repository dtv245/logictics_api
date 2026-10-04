package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Provider observation date is retained verbatim; retrieval is a distinct actual instant. */
public record FuelIndexObservation(String provider, String region, String seriesIdentifier,
        LocalDate observationDate, BigDecimal value, String currency, String unit,
        String frequency, String fuelType, boolean includingTaxes, Instant retrievedAt,
        String providerVersion, String contentHash) { }
