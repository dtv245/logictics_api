package com.company.logicstic.modules.notification.service.impl;

import com.company.logicstic.modules.notification.dto.response.NotificationResponse;
import com.company.logicstic.modules.notification.entity.Notification;
import com.company.logicstic.modules.notification.event.TenantNotificationEvent;
import com.company.logicstic.modules.notification.mapper.NotificationMapper;
import com.company.logicstic.modules.notification.repository.NotificationRepository;
import com.company.logicstic.modules.notification.service.NotificationService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl
    extends AbstractBaseService<Notification, NotificationResponse, Void>
    implements NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  public NotificationServiceImpl(
      NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
    super(notificationRepository, notificationMapper::toResponse, null, null);
    this.notificationRepository = notificationRepository;
    this.notificationMapper = notificationMapper;
  }

  @Override
  protected String entityName() {
    return "Notification";
  }

  public PagedResponse<NotificationResponse> list(int page, int pageSize) {
    var pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdDate").descending());
    return PagedResponse.from(
        notificationRepository.findAll(pageable).map(notificationMapper::toResponse));
  }

  @Transactional
  public int markAllAsRead() {
    return notificationRepository.markAllAsRead();
  }

  /** Persists an unread notification in the database of the current tenant. */
  @Transactional
  public NotificationResponse publish(TenantNotificationEvent event) {
    Notification notification = new Notification();
    notification.setTitle(event.title());
    notification.setMessage(event.message());
    notification.setIsRead(false);
    notification.setCreatedDate(OffsetDateTime.now());
    return notificationMapper.toResponse(notificationRepository.save(notification));
  }
}
