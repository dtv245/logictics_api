package com.company.logicstic.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "messages",
    schema = "public",
    indexes = {
      @Index(name = "ix_messages_conversation_id", columnList = "conversation_id"),
      @Index(name = "ix_messages_sender_id", columnList = "sender_id"),
      @Index(name = "ix_messages_sent_at", columnList = "sent_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Message {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private Employee sender;

  @Column(name = "\"content\"", nullable = false, length = 2000)
  private String content;

  @Column(name = "sent_at", nullable = false)
  private OffsetDateTime sentAt;

  @Column(name = "is_deleted", nullable = false)
  private Boolean isDeleted;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
