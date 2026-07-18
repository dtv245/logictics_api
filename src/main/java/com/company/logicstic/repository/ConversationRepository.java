package com.company.logicstic.repository;

import com.company.logicstic.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT DISTINCT c FROM Conversation c
            JOIN c.participants p
            WHERE p.employeeId = :employeeId
            ORDER BY c.lastMessageAt DESC
            """)
    Page<Conversation> findByParticipant(@Param("employeeId") UUID employeeId, Pageable pageable);
}
