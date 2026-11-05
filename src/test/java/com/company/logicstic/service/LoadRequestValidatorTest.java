package com.company.logicstic.service;

import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.exception.BadRequestException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadRequestValidatorTest {

    private static final OffsetDateTime PICKUP =
            OffsetDateTime.parse("2026-11-03T08:00:00Z");
    private static final OffsetDateTime DELIVERY =
            OffsetDateTime.parse("2026-11-04T08:00:00Z");

    @Test
    @DisplayName("delivery must be strictly after pickup")
    void rejectsDeliveryNotAfterPickup() {
        var equal = assertThrows(BadRequestException.class,
                () -> LoadRequestValidator.validate(request(PICKUP, PICKUP)));
        assertEquals("LOAD_DELIVERY_BEFORE_PICKUP", equal.getCode());

        var earlier = assertThrows(BadRequestException.class,
                () -> LoadRequestValidator.validate(request(PICKUP, PICKUP.minusHours(1))));
        assertEquals("LOAD_DELIVERY_BEFORE_PICKUP", earlier.getCode());
    }

    @Test
    @DisplayName("accepts a delivery after pickup and allows missing dates")
    void acceptsValidOrMissingSchedule() {
        assertDoesNotThrow(() -> LoadRequestValidator.validate(request(PICKUP, DELIVERY)));
        assertDoesNotThrow(() -> LoadRequestValidator.validate(request(PICKUP, null)));
        assertDoesNotThrow(() -> LoadRequestValidator.validate(request(null, DELIVERY)));
    }

    @Test
    @DisplayName("DTO annotations reject zero distance, out-of-range coordinates and non-positive cargo")
    void beanValidationRejectsInvalidMeasurements() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        var request = request(PICKUP, DELIVERY);
        assertTrue(validator.validate(request).isEmpty());

        assertEquals(1, validator.validate(copy(request, 0.0, 41.0, -87.0, 32.0, -96.0, 100.0, 10.0)).size());
        assertEquals(1, validator.validate(copy(request, 10.0, 91.0, -87.0, 32.0, -96.0, 100.0, 10.0)).size());
        assertEquals(1, validator.validate(copy(request, 10.0, 41.0, -181.0, 32.0, -96.0, 100.0, 10.0)).size());
        assertEquals(1, validator.validate(copy(request, 10.0, 41.0, -87.0, 32.0, -96.0, 0.0, 10.0)).size());
        assertEquals(1, validator.validate(copy(request, 10.0, 41.0, -87.0, 32.0, -96.0, 100.0, -1.0)).size());
    }

    private static CreateLoadRequest request(OffsetDateTime pickup, OffsetDateTime delivery) {
        return new CreateLoadRequest(
                "Load", "STANDARD", "draft", 10.0, false, UUID.randomUUID(),
                null, null, "MANUAL", pickup, delivery, null, false, null, null,
                null, null, null, null, null, null, BigDecimal.TEN, "USD",
                "1 Main St", null, "Chicago", "IL", "60601", "US", 41.0, -87.0,
                "2 Oak St", null, "Dallas", "TX", "75201", "US", 32.0, -96.0,
                null, null, 100.0, 10.0);
    }

    private static CreateLoadRequest copy(CreateLoadRequest r, double distance,
                                          double originLat, double originLon,
                                          double destLat, double destLon,
                                          double weight, double volume) {
        return new CreateLoadRequest(
                r.name(), r.type(), r.status(), distance, r.isInProximity(), r.customerId(),
                r.assignedTruckId(), r.assignedDispatcherId(), r.source(), r.requestedPickupDate(),
                r.requestedDeliveryDate(), r.notes(), r.isHazmat(), r.hazmatClass(), r.unNumber(),
                r.containerId(), r.originTerminalId(), r.destinationTerminalId(),
                r.externalSourceProvider(), r.externalSourceId(), r.externalBrokerReference(),
                r.deliveryCostAmount(), r.deliveryCostCurrency(),
                r.originAddressLine1(), r.originAddressLine2(), r.originAddressCity(),
                r.originAddressState(), r.originAddressZipCode(), r.originAddressCountry(),
                originLat, originLon,
                r.destinationAddressLine1(), r.destinationAddressLine2(), r.destinationAddressCity(),
                r.destinationAddressState(), r.destinationAddressZipCode(), r.destinationAddressCountry(),
                destLat, destLon,
                r.requestedPickupBusinessDate(), r.requestedPickupDateProvenance(), weight, volume);
    }
}
