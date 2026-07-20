package com.company.logicstic.modules.notification.mapper;

import com.company.logicstic.shared.config.MapperConfiguration;
import com.company.logicstic.modules.notification.dto.NotificationView;
import com.company.logicstic.modules.notification.entity.Notification;
import org.mapstruct.Mapper;

/**
 * Maps between {@link Notification} entity and its DTOs.
 * <p>
 * Notifications are created internally (not via API), so only {@code toView} is provided.
 * All fields map directly by name.
 */
@Mapper(config = MapperConfiguration.class)
public interface NotificationMapper {

    NotificationView toView(Notification notification);
}
