package com.company.logicstic.modules.document.dto;

import com.company.logicstic.modules.document.entity.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentView(
        UUID id,
        String ownerType,
        String fileName,
        String originalFileName,
        String contentType,
        Long fileSizeBytes,
        String blobPath,
        String blobContainer,
        String type,
        String status,
        String description,
        UUID uploadedById,
        String uploadedByName,
        UUID loadId,
        UUID truckId,
        UUID employeeId,
        String recipientName,
        OffsetDateTime capturedAt,
        Double captureLatitude,
        Double captureLongitude,
        String notes
) {
    public static DocumentView from(Document d) {
        String uploaderName = d.getUploadedBy() != null
                ? d.getUploadedBy().getFirstName() + " " + d.getUploadedBy().getLastName() : null;
        return new DocumentView(
                d.getId(), d.getOwnerType(), d.getFileName(), d.getOriginalFileName(),
                d.getContentType(), d.getFileSizeBytes(), d.getBlobPath(), d.getBlobContainer(),
                d.getType(), d.getStatus(), d.getDescription(),
                d.getUploadedBy() != null ? d.getUploadedBy().getId() : null, uploaderName,
                d.getLoad() != null ? d.getLoad().getId() : null,
                d.getTruck() != null ? d.getTruck().getId() : null,
                d.getEmployee() != null ? d.getEmployee().getId() : null,
                d.getRecipientName(), d.getCapturedAt(),
                d.getCaptureLatitude(), d.getCaptureLongitude(), d.getNotes()
        );
    }
}
