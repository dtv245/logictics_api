package com.company.logicstic.repository;

import com.company.logicstic.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId, Pageable pageable);

    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.conversation.id IN (
                SELECT p.conversation.id FROM ConversationParticipant p WHERE p.employeeId = :employeeId
            )
            AND m.isDeleted = false
            AND NOT EXISTS (
                SELECT r FROM MessageReadReceipt r WHERE r.message = m AND r.employeeId = :employeeId
            )
            """)
    long countUnread(@Param("employeeId") UUID employeeId);
}
