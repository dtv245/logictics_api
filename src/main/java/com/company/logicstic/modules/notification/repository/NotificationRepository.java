package com.company.logicstic.modules.notification.repository;

import com.company.logicstic.modules.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findAll(Pageable pageable);

  /**
   * Marks every unread notification of the current tenant as read.
   *
   * <p>No {@code @Transactional} here — docs/docs/development/engineering-conventions.md §7 puts
   * the transaction boundary on the service. {@code NotificationService.markAllAsRead()} supplies
   * it.
   *
   * @return the number of rows updated
   */
  @Modifying
  @Query("UPDATE Notification n SET n.isRead = true WHERE n.isRead = false")
  int markAllAsRead();
}
