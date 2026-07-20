package com.company.logicstic.modules.notification.repository;

import com.company.logicstic.modules.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findAll(Pageable pageable);

  @Transactional
  @Modifying
  @Query("UPDATE Notification n SET n.isRead = true WHERE n.isRead = false")
  int markAllAsRead();
}
