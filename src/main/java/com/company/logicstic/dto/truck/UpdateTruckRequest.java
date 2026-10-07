package com.company.logicstic.dto.truck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateTruckRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedVersion,
        @NotBlank String number,
        @NotBlank String type,
        @NotNull Integer vehicleCapacity,
        @NotBlank String status,
        String make,
        String model,
        Integer year,
        String vin,
        String licensePlate,
        String licensePlateState,
        @NotNull Boolean isHazmatPlacarded,
        UUID mainDriverId,
        UUID secondaryDriverId,
        Boolean adrEquipmentIsAdrCertified,
        String adrEquipmentAllowedClasses,
        String adrEquipmentOrangePlateNumber
) {
    public CreateTruckRequest toCreateRequest() {
        return new CreateTruckRequest(number, type, vehicleCapacity, status, make, model, year, vin, licensePlate, licensePlateState, isHazmatPlacarded, mainDriverId, secondaryDriverId, adrEquipmentIsAdrCertified, adrEquipmentAllowedClasses, adrEquipmentOrangePlateNumber);
    }
    public static UpdateTruckRequest from(CreateTruckRequest request, Long expectedVersion) {
        return new UpdateTruckRequest(expectedVersion, request.number(), request.type(), request.vehicleCapacity(), request.status(), request.make(), request.model(), request.year(), request.vin(), request.licensePlate(), request.licensePlateState(), request.isHazmatPlacarded(), request.mainDriverId(), request.secondaryDriverId(), request.adrEquipmentIsAdrCertified(), request.adrEquipmentAllowedClasses(), request.adrEquipmentOrangePlateNumber());
    }
}
