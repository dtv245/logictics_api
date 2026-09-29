package com.company.logicstic.dto.inspection;

import com.company.logicstic.entity.LoadConditionReport;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionView(
        UUID id,
        UUID loadId,
        String type,
        String vin,
        Integer vehicleYear,
        String vehicleMake,
        String vehicleModel,
        String vehicleBodyClass,
        String containerNumber,
        String sealNumber,
        String notes,
        Double latitude,
        Double longitude,
        OffsetDateTime inspectedAt,
        UUID inspectedById,
        String inspectedByName
) {
    public static InspectionView from(LoadConditionReport r) {
        String name = r.getInspectedBy() != null
                ? r.getInspectedBy().getFirstName() + " " + r.getInspectedBy().getLastName() : null;
        return new InspectionView(
                r.getId(), r.getLoad().getId(), r.getType(),
                r.getVin(), r.getVehicleYear(), r.getVehicleMake(), r.getVehicleModel(), r.getVehicleBodyClass(),
                r.getContainerNumber(), r.getSealNumber(), r.getNotes(),
                r.getLatitude(), r.getLongitude(), r.getInspectedAt(),
                r.getInspectedBy() != null ? r.getInspectedBy().getId() : null, name
        );
    }
}
