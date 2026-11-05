package com.company.logicstic.repository;

import com.company.logicstic.entity.AiLoadGapHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiLoadGapHistoryRepository extends JpaRepository<AiLoadGapHistory, UUID> {

    List<AiLoadGapHistory> findByGapIdOrderByCreatedAtDesc(UUID gapId);
}
