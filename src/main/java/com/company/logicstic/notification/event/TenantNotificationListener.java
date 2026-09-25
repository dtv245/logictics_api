package com.company.logicstic.notification.event;

import com.company.logicstic.notification.NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!nodb")
public class TenantNotificationListener {

  private final NotificationService notificationService;

  public TenantNotificationListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @EventListener
  public void onTenantNotification(TenantNotificationEvent event) {
    notificationService.publish(event);
  }
}
