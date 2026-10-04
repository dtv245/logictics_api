package com.company.logicstic.service.rating;

import com.company.logicstic.service.rating.domain.FuelIndexObservation;
import java.time.LocalDate;
import java.util.List;

/** No database transaction or financial snapshot write is part of provider retrieval. */
public interface FuelIndexProvider {
    List<FuelIndexObservation> observations(String region, LocalDate pricingDate);
}
