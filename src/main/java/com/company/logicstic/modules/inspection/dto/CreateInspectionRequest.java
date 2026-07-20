package com.company.logicstic.modules.inspection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateInspectionRequest(
    @NotNull UUID loadId,
    @NotBlank String type,
    String vin,
    Integer vehicleYear,
    String vehicleMake,
    String vehicleModel,
    String vehicleBodyClass,
    String containerNumber,
    String sealNumber,
    String notes,
    String inspectorSignature,
    Double latitude,
    Double longitude,
    @NotNull OffsetDateTime inspectedAt,
    @NotNull UUID inspectedById,
    @NotNull @Valid List<DefectRequest> defects) {}
