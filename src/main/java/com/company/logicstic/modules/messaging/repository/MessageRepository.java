package com.company.logicstic.modules.messaging.repository;

import com.company.logicstic.modules.messaging.entity.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  Page<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId, Pageable pageable);

  @Query(
      """
            SELECT COUNT(m) FROM Message m
            WHERE m.conversation.id IN (
                SELECT p.conversation.id FROM ConversationParticipant p WHERE p.employee.id = :employeeId
            )
            AND m.isDeleted = false
            AND m.sender.id <> :employeeId
            AND NOT EXISTS (
                SELECT r FROM MessageReadReceipt r WHERE r.message = m AND r.readBy.id = :employeeId
            )
            """)
  long countUnread(@Param("employeeId") UUID employeeId);

  @Query(
      """
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId
              AND m.isDeleted = false
              AND m.sender.id <> :employeeId
              AND NOT EXISTS (
                  SELECT r FROM MessageReadReceipt r
                  WHERE r.message = m AND r.readBy.id = :employeeId
              )
            """)
  List<Message> findUnread(
      @Param("conversationId") UUID conversationId, @Param("employeeId") UUID employeeId);
}
