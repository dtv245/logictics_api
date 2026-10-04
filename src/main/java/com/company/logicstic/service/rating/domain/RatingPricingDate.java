package com.company.logicstic.service.rating.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Input provenance to be preserved verbatim by consequential 6F accepted snapshots. */
public record RatingPricingDate(LocalDate pricingDate, String pricingDateSource, UUID sourceChangeId) { }
