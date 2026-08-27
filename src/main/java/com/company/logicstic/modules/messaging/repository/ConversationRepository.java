package com.company.logicstic.modules.messaging.repository;

import com.company.logicstic.modules.messaging.entity.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  @Query(
      """
            SELECT DISTINCT c FROM Conversation c
            JOIN c.participants p
            WHERE c.id = :conversationId
              AND p.employee.id = :employeeId
            """)
  Optional<Conversation> findByIdAndParticipant(
      @Param("conversationId") UUID conversationId, @Param("employeeId") UUID employeeId);

  @Query(
      """
            SELECT DISTINCT c FROM Conversation c
            JOIN c.participants p
            WHERE p.employee.id = :employeeId
            ORDER BY c.lastMessageAt DESC
            """)
  Page<Conversation> findByParticipant(@Param("employeeId") UUID employeeId, Pageable pageable);
}
