package com.company.logicstic.dto.document;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import com.company.logicstic.entity.Document;
import com.company.logicstic.service.DriverExpiryStatusCalculator;

import java.time.LocalDate;
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
        String notes,
        LocalDate expiryDate,
        ExpiryStatus expiryStatus
) {
    public static DocumentView from(Document d) {
        return from(d, new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public static DocumentView from(Document d, DriverExpiryStatusCalculator expiryCalculator) {
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
                d.getCaptureLatitude(), d.getCaptureLongitude(), d.getNotes(), d.getExpiryDate(),
                expiryCalculator.status(d.getExpiryDate())
        );
    }
}
