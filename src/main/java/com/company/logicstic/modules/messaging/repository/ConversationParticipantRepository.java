package com.company.logicstic.modules.messaging.repository;

import com.company.logicstic.modules.messaging.entity.ConversationParticipant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository
    extends JpaRepository<ConversationParticipant, UUID> {

  boolean existsByConversationIdAndEmployeeId(UUID conversationId, UUID employeeId);

  Optional<ConversationParticipant> findByConversationIdAndEmployeeId(
      UUID conversationId, UUID employeeId);
}
