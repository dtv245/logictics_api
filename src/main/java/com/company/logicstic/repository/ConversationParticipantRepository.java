package com.company.logicstic.repository;

import com.company.logicstic.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {
    boolean existsByConversationIdAndEmployeeId(UUID conversationId, UUID employeeId);
    Optional<ConversationParticipant> findByConversationIdAndEmployeeId(UUID conversationId, UUID employeeId);
}
