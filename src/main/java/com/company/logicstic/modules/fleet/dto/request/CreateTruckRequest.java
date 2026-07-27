package com.company.logicstic.modules.fleet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTruckRequest(
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
    String adrEquipmentOrangePlateNumber) {}
