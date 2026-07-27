package com.company.logicstic.modules.inspection.dto.response;

import com.company.logicstic.modules.inspection.entity.LoadConditionReport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(
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
    String inspectedByName,
    List<DefectResponse> defects) {
  public static InspectionResponse from(LoadConditionReport r) {
    String name =
        r.getInspectedBy() != null
            ? r.getInspectedBy().getFirstName() + " " + r.getInspectedBy().getLastName()
            : null;
    return new InspectionResponse(
        r.getId(),
        r.getLoad().getId(),
        r.getType(),
        r.getVin(),
        r.getVehicleYear(),
        r.getVehicleMake(),
        r.getVehicleModel(),
        r.getVehicleBodyClass(),
        r.getContainerNumber(),
        r.getSealNumber(),
        r.getNotes(),
        r.getLatitude(),
        r.getLongitude(),
        r.getInspectedAt(),
        r.getInspectedBy() != null ? r.getInspectedBy().getId() : null,
        name,
        r.getDefects().stream().map(DefectResponse::from).toList());
  }
}
