package com.company.logicstic.modules.trip.dto.response;

import com.company.logicstic.modules.trip.entity.Trip;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TripResponse(
    UUID id,
    Long number,
    String name,
    Double totalDistance,
    String status,
    OffsetDateTime dispatchedAt,
    OffsetDateTime completedAt,
    OffsetDateTime cancelledAt,
    UUID truckId,
    String truckNumber,
    List<TripStopResponse> stops) {
  public static TripResponse from(Trip t) {
    return new TripResponse(
        t.getId(),
        t.getNumber(),
        t.getName(),
        t.getTotalDistance(),
        t.getStatus(),
        t.getDispatchedAt(),
        t.getCompletedAt(),
        t.getCancelledAt(),
        t.getTruck() != null ? t.getTruck().getId() : null,
        t.getTruck() != null ? t.getTruck().getNumber() : null,
        t.getStops().stream().map(TripStopResponse::from).toList());
  }
}
