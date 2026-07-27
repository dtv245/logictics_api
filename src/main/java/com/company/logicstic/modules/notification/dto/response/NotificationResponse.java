package com.company.logicstic.modules.notification.dto.response;

import com.company.logicstic.modules.notification.entity.Notification;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id, String title, String message, Boolean isRead, OffsetDateTime createdDate) {
  public static NotificationResponse from(Notification n) {
    return new NotificationResponse(
        n.getId(), n.getTitle(), n.getMessage(), n.getIsRead(), n.getCreatedDate());
  }
}
