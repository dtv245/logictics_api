package com.company.logicstic.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.notification.entity.Notification;
import com.company.logicstic.modules.notification.event.TenantNotificationEvent;
import com.company.logicstic.modules.notification.event.TenantNotificationListener;
import com.company.logicstic.modules.notification.mapper.NotificationMapper;
import com.company.logicstic.modules.notification.repository.NotificationRepository;
import com.company.logicstic.modules.notification.service.NotificationService;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class NotificationProducerTest {

  @Test
  void persistsTenantEventAsUnreadNotification() {
    AtomicReference<Notification> stored = new AtomicReference<>();
    NotificationRepository repository =
        proxy(
            NotificationRepository.class,
            (method, args) -> {
              if (method.equals("save")) {
                Notification notification = (Notification) args[0];
                notification.setId(UUID.randomUUID());
                stored.set(notification);
                return notification;
              }
              throw new AssertionError("Unexpected NotificationRepository call: " + method);
            });
    NotificationService service =
        new NotificationService(repository, Mappers.getMapper(NotificationMapper.class));
    TenantNotificationListener listener = new TenantNotificationListener(service);

    listener.onTenantNotification(
        new TenantNotificationEvent(
            "Trip dispatched", "Trip changed status to dispatched.", "Trip", UUID.randomUUID()));

    assertThat(stored.get().getTitle()).isEqualTo("Trip dispatched");
    assertThat(stored.get().getMessage()).contains("dispatched");
    assertThat(stored.get().getIsRead()).isFalse();
    assertThat(stored.get().getCreatedDate()).isNotNull();
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, RepositoryCall call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> call.invoke(method.getName(), args));
  }

  @FunctionalInterface
  private interface RepositoryCall {
    Object invoke(String method, Object[] args);
  }
}
