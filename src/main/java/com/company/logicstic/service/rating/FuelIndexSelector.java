package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FuelIndexSelector {
    public FuelIndexObservation select(IndexBasedFscPolicy policy, LocalDate date, List<FuelIndexObservation> rows) {
        if (date == null) throw new BadRequestException("RATING_PRICING_DATE_REQUIRED", "Pickup business date required");
        if (policy.maxIndexAgeDays() == null || policy.maxIndexAgeDays() < 0)
            throw new BadRequestException("INVALID_RATE_POLICY", "Explicit nonnegative maximum index age required");
        String series = EiaFuelIndexProvider.seriesFor(policy.indexRegion());
        var candidates = (rows == null ? List.<FuelIndexObservation>of() : rows).stream()
                .filter(o -> o != null && "EIA".equals(o.provider()) && policy.indexRegion().equals(o.region())
                        && series.equals(o.seriesIdentifier()) && o.observationDate() != null && !o.observationDate().isAfter(date))
                .toList();
        var latest = candidates.stream().max(Comparator.comparing(FuelIndexObservation::observationDate))
                .orElseThrow(() -> new BadRequestException("FUEL_INDEX_UNAVAILABLE", "No eligible index observation"));
        if (candidates.stream().filter(o -> o.observationDate().equals(latest.observationDate())).count() != 1
                || latest.value() == null || latest.value().signum() < 0 || !"USD".equals(latest.currency())
                || !"GALLON".equals(latest.unit()) || !"WEEKLY".equals(latest.frequency())
                || !"ULSD".equals(latest.fuelType()) || !latest.includingTaxes() || latest.retrievedAt() == null
                || latest.providerVersion() == null || latest.providerVersion().isBlank()
                || latest.contentHash() == null || !latest.contentHash().matches("[0-9a-f]{64}"))
            throw new BadRequestException("FUEL_INDEX_UNAVAILABLE", "Index identity, provenance or unique observation is unavailable");
        if (ChronoUnit.DAYS.between(latest.observationDate(), date) > policy.maxIndexAgeDays())
            throw new BadRequestException("FUEL_INDEX_STALE", "Eligible fuel index exceeds the configured maximum age");
        return latest;
    }
}
