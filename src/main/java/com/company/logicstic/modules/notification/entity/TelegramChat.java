package com.company.logicstic.modules.notification.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "telegram_chats",
    schema = "public",
    indexes = {@Index(name = "ix_telegram_chats_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class TelegramChat {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "chat_id", nullable = false, unique = true)
  private Long chatId;

  @Column(name = "chat_type", nullable = false, columnDefinition = "text")
  private String chatType;

  @Column(name = "\"role\"", columnDefinition = "text")
  private String role;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "username", length = 128)
  private String username;

  @Column(name = "first_name", length = 128)
  private String firstName;

  @Column(name = "group_title", length = 256)
  private String groupTitle;

  @Column(name = "notifications_enabled", nullable = false)
  private Boolean notificationsEnabled;

  @Column(name = "connected_at", nullable = false)
  private OffsetDateTime connectedAt;

  @Column(name = "last_interaction_at")
  private OffsetDateTime lastInteractionAt;
}
