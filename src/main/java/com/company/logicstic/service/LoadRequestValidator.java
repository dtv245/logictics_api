package com.company.logicstic.service;

import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.exception.BadRequestException;

import java.time.OffsetDateTime;

/**
 * Cross-field rules that bean validation cannot express on a flat request.
 *
 * <p>Field-level rules (positive distance, coordinate ranges, positive cargo
 * weight/volume) stay on {@link CreateLoadRequest} annotations so the API keeps
 * returning them as per-field errors.</p>
 */
public final class LoadRequestValidator {

    private LoadRequestValidator() {
    }

    /**
     * A requested delivery must happen strictly after the requested pickup.
     * The frontend uses the same strict rule; both instants are nullable and
     * the rule only applies when both are supplied.
     */
    public static void validate(CreateLoadRequest request) {
        if (request == null) {
            return;
        }
        OffsetDateTime pickup = request.requestedPickupDate();
        OffsetDateTime delivery = request.requestedDeliveryDate();
        if (pickup != null && delivery != null && !delivery.isAfter(pickup)) {
            throw new BadRequestException(
                    "LOAD_DELIVERY_BEFORE_PICKUP",
                    "Requested delivery must be after requested pickup");
        }
    }
}
