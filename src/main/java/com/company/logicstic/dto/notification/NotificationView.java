package com.company.logicstic.dto.notification;

import com.company.logicstic.entity.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationView(
        UUID id,
        String title,
        String message,
        Boolean isRead,
        OffsetDateTime createdDate
) {
    public static NotificationView from(Notification n) {
        return new NotificationView(n.getId(), n.getTitle(), n.getMessage(), n.getIsRead(), n.getCreatedDate());
    }
}
