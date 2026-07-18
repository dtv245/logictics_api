package com.company.logicstic.dto.truck;

import com.company.logicstic.entity.Truck;

import java.util.UUID;

public record TruckView(
        UUID id,
        String number,
        String type,
        Integer vehicleCapacity,
        String status,
        String make,
        String model,
        Integer year,
        String vin,
        String licensePlate,
        String licensePlateState,
        Boolean isHazmatPlacarded,
        UUID mainDriverId,
        String mainDriverName,
        UUID secondaryDriverId,
        String secondaryDriverName,
        Boolean adrEquipmentIsAdrCertified,
        String adrEquipmentAllowedClasses,
        Double currentLocationLatitude,
        Double currentLocationLongitude
) {
    public static TruckView from(Truck t) {
        String mainName = t.getMainDriver() != null
                ? t.getMainDriver().getFirstName() + " " + t.getMainDriver().getLastName() : null;
        String secondaryName = t.getSecondaryDriver() != null
                ? t.getSecondaryDriver().getFirstName() + " " + t.getSecondaryDriver().getLastName() : null;
        return new TruckView(
                t.getId(), t.getNumber(), t.getType(), t.getVehicleCapacity(),
                t.getStatus(), t.getMake(), t.getModel(), t.getYear(),
                t.getVin(), t.getLicensePlate(), t.getLicensePlateState(),
                t.getIsHazmatPlacarded(),
                t.getMainDriver() != null ? t.getMainDriver().getId() : null, mainName,
                t.getSecondaryDriver() != null ? t.getSecondaryDriver().getId() : null, secondaryName,
                t.getAdrEquipmentIsAdrCertified(), t.getAdrEquipmentAllowedClasses(),
                t.getCurrentLocationLatitude(), t.getCurrentLocationLongitude()
        );
    }
}
