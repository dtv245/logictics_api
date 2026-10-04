package com.company.logicstic.service.rating.domain;

import java.time.LocalDate;
import java.util.UUID;

/** pricingDate must be provided by a proven Load business-date adapter, not a clock. */
public record RateMatchContext(UUID customerId, UUID contractId, Integer contractVersion,
                               String lane, String equipment, String service, String tier,
                               String currency, LocalDate pricingDate) { }
