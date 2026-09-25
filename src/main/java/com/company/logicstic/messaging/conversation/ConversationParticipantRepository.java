package com.company.logicstic.messaging.conversation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository
    extends JpaRepository<ConversationParticipant, UUID> {

  boolean existsByConversationIdAndEmployeeId(UUID conversationId, UUID employeeId);

  Optional<ConversationParticipant> findByConversationIdAndEmployeeId(
      UUID conversationId, UUID employeeId);
}
