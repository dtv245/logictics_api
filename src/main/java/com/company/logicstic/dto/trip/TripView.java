package com.company.logicstic.dto.trip;

import com.company.logicstic.entity.Trip;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TripView(
        UUID id,
        Long version,
        Long number,
        String name,
        Double totalDistance,
        String status,
        OffsetDateTime dispatchedAt,
        OffsetDateTime completedAt,
        OffsetDateTime cancelledAt,
        UUID truckId,
        String truckNumber
) {
    public static TripView from(Trip t) {
        return new TripView(
                t.getId(), t.getVersion(), t.getNumber(), t.getName(), t.getTotalDistance(), t.getStatus(),
                t.getDispatchedAt(), t.getCompletedAt(), t.getCancelledAt(),
                t.getTruck() != null ? t.getTruck().getId() : null,
                t.getTruck() != null ? t.getTruck().getNumber() : null
        );
    }
}
