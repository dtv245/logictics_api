package com.company.logicstic.dto.load;

import com.company.logicstic.entity.Load;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoadView(
        UUID id,
        Long number,
        String name,
        String type,
        String status,
        Double distance,
        Boolean isInProximity,
        OffsetDateTime dispatchedAt,
        OffsetDateTime pickedUpAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime cancelledAt,
        UUID customerId,
        String customerName,
        UUID assignedTruckId,
        String assignedTruckNumber,
        UUID assignedDispatcherId,
        String assignedDispatcherName,
        String source,
        OffsetDateTime requestedPickupDate,
        OffsetDateTime requestedDeliveryDate,
        String notes,
        Boolean isHazmat,
        String hazmatClass,
        String unNumber,
        BigDecimal deliveryCostAmount,
        String deliveryCostCurrency,
        String originAddressLine1,
        String originAddressLine2,
        String originAddressCity,
        String originAddressState,
        String originAddressZipCode,
        String originAddressCountry,
        Double originLocationLatitude,
        Double originLocationLongitude,
        String destinationAddressLine1,
        String destinationAddressLine2,
        String destinationAddressCity,
        String destinationAddressState,
        String destinationAddressZipCode,
        String destinationAddressCountry,
        Double destinationLocationLatitude,
        Double destinationLocationLongitude
) {
    public static LoadView from(Load l) {
        String dispatcherName = l.getAssignedDispatcher() != null
                ? l.getAssignedDispatcher().getFirstName() + " " + l.getAssignedDispatcher().getLastName() : null;
        return new LoadView(
                l.getId(), l.getNumber(), l.getName(), l.getType(), l.getStatus(),
                l.getDistance(), l.getIsInProximity(),
                l.getDispatchedAt(), l.getPickedUpAt(), l.getDeliveredAt(), l.getCancelledAt(),
                l.getCustomer() != null ? l.getCustomer().getId() : null,
                l.getCustomer() != null ? l.getCustomer().getName() : null,
                l.getAssignedTruck() != null ? l.getAssignedTruck().getId() : null,
                l.getAssignedTruck() != null ? l.getAssignedTruck().getNumber() : null,
                l.getAssignedDispatcher() != null ? l.getAssignedDispatcher().getId() : null,
                dispatcherName,
                l.getSource(), l.getRequestedPickupDate(), l.getRequestedDeliveryDate(),
                l.getNotes(), l.getIsHazmat(), l.getHazmatClass(), l.getUnNumber(),
                l.getDeliveryCostAmount(), l.getDeliveryCostCurrency(),
                l.getOriginAddressLine1(), l.getOriginAddressLine2(), l.getOriginAddressCity(),
                l.getOriginAddressState(), l.getOriginAddressZipCode(), l.getOriginAddressCountry(),
                l.getOriginLocationLatitude(), l.getOriginLocationLongitude(),
                l.getDestinationAddressLine1(), l.getDestinationAddressLine2(), l.getDestinationAddressCity(),
                l.getDestinationAddressState(), l.getDestinationAddressZipCode(), l.getDestinationAddressCountry(),
                l.getDestinationLocationLatitude(), l.getDestinationLocationLongitude()
        );
    }
}
