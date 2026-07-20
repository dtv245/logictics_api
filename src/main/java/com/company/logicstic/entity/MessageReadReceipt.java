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
    name = "message_read_receipts",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "ix_message_read_receipts_message_id_read_by_id",
          columnNames = {"message_id", "read_by_id"})
    },
    indexes = {@Index(name = "ix_message_read_receipts_read_by_id", columnList = "read_by_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadReceipt {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "message_id", nullable = false)
  private Message message;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "read_by_id", nullable = false)
  private Employee readBy;

  @Column(name = "read_at", nullable = false)
  private OffsetDateTime readAt;
}
