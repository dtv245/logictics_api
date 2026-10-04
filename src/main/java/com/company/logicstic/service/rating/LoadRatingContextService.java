package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.service.rating.domain.RatingPricingDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class LoadRatingContextService {
    private final LoadRepository loads;
    public RatingPricingDate pricingDate(UUID loadId) {
        var load = loads.findById(loadId).orElseThrow(() -> new ResourceNotFoundException("Load not found"));
        if (load.getRequestedPickupBusinessDate() == null)
            throw new BadRequestException("RATING_PRICING_DATE_REQUIRED", "Explicit Load requested pickup business LocalDate required");
        return new RatingPricingDate(load.getRequestedPickupBusinessDate(), "LOAD_REQUESTED_PICKUP_DATE", load.getPickupBusinessDateChangeId());
    }
}
