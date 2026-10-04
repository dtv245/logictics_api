package com.company.logicstic.service.rating;

import com.company.logicstic.entity.Customer;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RatingMileageServiceTest {
    private final RatingMileageRepository mileages = mock(RatingMileageRepository.class);
    private final LoadRepository loads = mock(LoadRepository.class);
    private final RatePolicyService policies = mock(RatePolicyService.class);
    private final LoadRatingContextService dates = mock(LoadRatingContextService.class);
    private final RatingMileageService service = new RatingMileageService(mileages, loads, mock(EmployeeRepository.class), policies, dates);
    private final UUID id = UUID.randomUUID();
    private RateRule rule(RatingMileageBasis basis) {
        return new RateRule(UUID.randomUUID(),1,10,null,null,null,null,null,null,null,"USD",LocalDate.of(2026,1,1),null,
                RatingMethod.PER_MILE,BigDecimal.ONE,basis,null,null,null,"RatingPolicyV1",1,UUID.randomUUID(),OffsetDateTime.now());
    }
    private void load() {
        var l = new Load(); var c = new Customer(); c.setId(UUID.randomUUID()); l.setCustomer(c);
        when(loads.findById(id)).thenReturn(Optional.of(l));
        when(dates.pricingDate(id)).thenReturn(new RatingPricingDate(LocalDate.of(2026,1,1), "LOAD_REQUESTED_PICKUP_DATE", UUID.randomUUID()));
    }
    @Test void minimumIsNotApplicableNotZeroMiles() {
        var r = service.resolve(id, RatingMileageComponent.MINIMUM_CHARGE, null, null, rule(null));
        assertEquals("NOT_APPLICABLE", r.mileageBasis()); assertNull(r.eligibleMiles()); verifyNoInteractions(mileages, loads, dates);
    }
    @Test void plannedAndActualNeverFallbackToOtherOrTripLegacyFields() {
        load();
        for (var basis : new RatingMileageBasis[] {RatingMileageBasis.PLANNED_LOAD_MILES, RatingMileageBasis.ACTUAL_LOADED_MILES, RatingMileageBasis.ACTUAL_ALL_MILES})
            assertEquals("RATE_MILEAGE_UNAVAILABLE", assertThrows(BadRequestException.class,
                    () -> service.resolve(id, RatingMileageComponent.LINEHAUL, basis, null, rule(basis))).getCode());
        verify(mileages, never()).evidence(any());
    }
    @Test void multiLoadTripNeedsExplicitAttributionRatherThanAllocation() {
        load(); when(mileages.hasMultiLoadTrip(id)).thenReturn(true);
        assertEquals("RATE_MILEAGE_ATTRIBUTION_REQUIRED", assertThrows(BadRequestException.class,
                () -> service.resolve(id, RatingMileageComponent.LINEHAUL, RatingMileageBasis.ACTUAL_ALL_MILES, null, rule(RatingMileageBasis.ACTUAL_ALL_MILES))).getCode());
    }
    @Test void componentCannotOverridePolicyBasisAndMissingEvidenceIsNotZero() {
        assertEquals("RATING_VALIDATION_REQUIRED", assertThrows(BadRequestException.class,
                () -> service.resolve(id, RatingMileageComponent.LINEHAUL, RatingMileageBasis.ACTUAL_ALL_MILES, null, rule(RatingMileageBasis.CONTRACT_MILES))).getCode());
        load();
        assertEquals("RATE_MILEAGE_UNAVAILABLE", assertThrows(BadRequestException.class,
                () -> service.resolve(id, RatingMileageComponent.LINEHAUL, RatingMileageBasis.CONTRACT_MILES, null, rule(RatingMileageBasis.CONTRACT_MILES))).getCode());
    }
}
