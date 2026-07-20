package com.company.logicstic.modules.notification.service;

import com.company.logicstic.modules.notification.dto.NotificationView;
import com.company.logicstic.modules.notification.entity.Notification;
import com.company.logicstic.modules.notification.event.TenantNotificationEvent;
import com.company.logicstic.modules.notification.mapper.NotificationMapper;
import com.company.logicstic.modules.notification.repository.NotificationRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class NotificationService extends AbstractBaseService<Notification, NotificationView, Void> {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  public NotificationService(
      NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
    super(notificationRepository, notificationMapper::toView, null, null);
    this.notificationRepository = notificationRepository;
    this.notificationMapper = notificationMapper;
  }

  @Override
  protected String entityName() {
    return "Notification";
  }

  public PagedResponse<NotificationView> list(int page, int pageSize) {
    var pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdDate").descending());
    return PagedResponse.from(
        notificationRepository.findAll(pageable).map(notificationMapper::toView));
  }

  @Transactional
  public int markAllAsRead() {
    return notificationRepository.markAllAsRead();
  }

  /** Persists an unread notification in the database of the current tenant. */
  @Transactional
  public NotificationView publish(TenantNotificationEvent event) {
    Notification notification = new Notification();
    notification.setTitle(event.title());
    notification.setMessage(event.message());
    notification.setIsRead(false);
    notification.setCreatedDate(OffsetDateTime.now());
    return notificationMapper.toView(notificationRepository.save(notification));
  }
}
