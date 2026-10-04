package com.company.logicstic.service.rating.domain;

import java.util.List;
import java.util.UUID;

public record RatingInputs(UUID loadId, RatingPricingDate pricingDate, RateMatchContext matchContext,
        String contextSource, RateRule rule, ResolvedRatingMileage linehaulMileage,
        ResolvedRatingMileage fscMileage, List<RatingAccessorialInput> accessorials) {
    public RatingInputs { accessorials = List.copyOf(accessorials); }
}
