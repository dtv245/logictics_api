package com.company.logicstic.modules.trip.dto;

import com.company.logicstic.modules.load.entity.TripStop;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TripStopView(
    UUID id,
    String type,
    Integer order,
    UUID loadId,
    OffsetDateTime arrivedAt,
    String addressLine1,
    String addressLine2,
    String addressCity,
    String addressState,
    String addressZipCode,
    String addressCountry,
    Double locationLatitude,
    Double locationLongitude) {

  public static TripStopView from(TripStop stop) {
    return new TripStopView(
        stop.getId(),
        stop.getType(),
        stop.getOrder(),
        stop.getLoad().getId(),
        stop.getArrivedAt(),
        stop.getAddressLine1(),
        stop.getAddressLine2(),
        stop.getAddressCity(),
        stop.getAddressState(),
        stop.getAddressZipCode(),
        stop.getAddressCountry(),
        stop.getLocationLatitude(),
        stop.getLocationLongitude());
  }
}
