package com.company.logicstic.modules.notification.service;

import com.company.logicstic.modules.notification.dto.response.NotificationResponse;
import com.company.logicstic.modules.notification.entity.Notification;
import com.company.logicstic.modules.notification.event.TenantNotificationEvent;
import com.company.logicstic.shared.dto.PagedResponse;
import java.util.UUID;

/**
 * Public API of the notification feature.
 *
 * <p>Like {@code DocumentService} this interface does not extend {@code CrudService}: notifications
 * are never created from a client request. They are produced by {@link #publish} from a domain
 * event after the originating transaction commits, so a rolled-back change never notifies anyone
 * (docs/docs/development/engineering-conventions.md §7).
 */
public interface NotificationService {

  /**
   * Lists notifications of the current tenant, newest first.
   *
   * @param page 1-based page number
   */
  PagedResponse<NotificationResponse> list(int page, int pageSize);

  NotificationResponse getById(UUID id);

  /** Returns the managed entity for another feature that needs it as an association target. */
  Notification getEntityById(UUID id);

  /**
   * Marks every unread notification of the current tenant as read.
   *
   * @return the number of notifications updated
   */
  int markAllAsRead();

  /** Persists an unread notification in the database of the current tenant. */
  NotificationResponse publish(TenantNotificationEvent event);
}
