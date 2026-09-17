package com.company.logicstic.notification;

import com.company.logicstic.config.MapperConfiguration;
import org.mapstruct.Mapper;

/**
 * Maps between {@link Notification} entity and its DTOs.
 *
 * <p>Notifications are created internally (not via API), so only {@code toResponse} is provided.
 * All fields map directly by name.
 */
@Mapper(config = MapperConfiguration.class)
public interface NotificationMapper {

  NotificationResponse toResponse(Notification notification);
}
