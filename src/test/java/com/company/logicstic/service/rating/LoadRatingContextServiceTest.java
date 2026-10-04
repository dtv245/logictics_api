package com.company.logicstic.service.rating;

import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.LoadRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LoadRatingContextServiceTest {
    @Test void readsExplicitLocalDateAndNeverConvertsTimestamp() {
        var loads = mock(LoadRepository.class); var load = new Load(); UUID id = UUID.randomUUID();
        load.setRequestedPickupDate(OffsetDateTime.parse("2030-12-31T23:59:00-12:00"));
        load.setRequestedPickupBusinessDate(LocalDate.of(2026,1,1)); load.setPickupBusinessDateChangeId(UUID.randomUUID());
        when(loads.findById(id)).thenReturn(Optional.of(load));
        var result = new LoadRatingContextService(loads).pricingDate(id);
        assertEquals(LocalDate.of(2026,1,1), result.pricingDate()); assertEquals("LOAD_REQUESTED_PICKUP_DATE", result.pricingDateSource());
    }
    @Test void timestampExistsButMissingDateMustReject() {
        var loads = mock(LoadRepository.class); var load = new Load(); UUID id = UUID.randomUUID();
        load.setRequestedPickupDate(OffsetDateTime.now()); when(loads.findById(id)).thenReturn(Optional.of(load));
        assertEquals("RATING_PRICING_DATE_REQUIRED", assertThrows(BadRequestException.class, () -> new LoadRatingContextService(loads).pricingDate(id)).getCode());
    }
}
