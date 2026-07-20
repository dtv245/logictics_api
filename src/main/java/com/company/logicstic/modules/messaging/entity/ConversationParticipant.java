package com.company.logicstic.modules.messaging.entity;

import com.company.logicstic.modules.employee.entity.Employee;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "conversation_participants",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_conversation_participants_conversation_id_employee_id",
          columnNames = {"conversation_id", "employee_id"})
    },
    indexes = {
      @Index(name = "ix_conversation_participants_employee_id", columnList = "employee_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class ConversationParticipant {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "joined_at", nullable = false)
  private OffsetDateTime joinedAt;

  @Column(name = "last_read_at")
  private OffsetDateTime lastReadAt;

  @Column(name = "is_muted", nullable = false)
  private Boolean isMuted;
}
