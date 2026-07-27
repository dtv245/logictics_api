package com.company.logicstic.modules.messaging.entity;

import com.company.logicstic.modules.load.entity.Load;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "conversations",
    schema = "public",
    indexes = {
      @Index(name = "ix_conversations_last_message_at", columnList = "last_message_at"),
      @Index(name = "ix_conversations_load_id", columnList = "load_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"name\"", length = 200)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "load_id")
  private Load load;

  @Column(name = "is_tenant_chat", nullable = false)
  private Boolean isTenantChat;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_message_at")
  private OffsetDateTime lastMessageAt;

  @OneToMany(
      mappedBy = "conversation",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<Message> messages = new ArrayList<>();

  @OneToMany(
      mappedBy = "conversation",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      orphanRemoval = true)
  private List<ConversationParticipant> participants = new ArrayList<>();
}
