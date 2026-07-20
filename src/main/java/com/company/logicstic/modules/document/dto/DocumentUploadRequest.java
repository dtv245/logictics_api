package com.company.logicstic.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentUploadRequest(
    @NotBlank String ownerType,
    @NotBlank String type,
    String description,
    @NotNull UUID uploadedById,
    UUID loadId,
    UUID truckId,
    UUID employeeId,
    String recipientName,
    OffsetDateTime capturedAt,
    Double captureLatitude,
    Double captureLongitude,
    String notes) {}
