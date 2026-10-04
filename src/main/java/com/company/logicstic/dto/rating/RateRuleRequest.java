package com.company.logicstic.dto.rating;

import com.company.logicstic.service.rating.domain.IndexBasedFscPolicy;
import com.company.logicstic.service.rating.domain.RatingMethod;
import com.company.logicstic.service.rating.domain.RatingMileageBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RateRuleRequest(Integer priority, UUID customerId, UUID contractId, Integer contractVersion,
                              String lane, String equipment, String service, String tier, String currency,
                              LocalDate effectiveFrom, LocalDate effectiveTo, RatingMethod method,
                              BigDecimal baseRate, RatingMileageBasis linehaulMileageBasis,
                              BigDecimal minimumCharge, BigDecimal maximumCharge, IndexBasedFscPolicy fsc) { }
