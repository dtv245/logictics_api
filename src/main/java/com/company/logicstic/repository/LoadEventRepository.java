package com.company.logicstic.repository;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.LoadEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadEventRepository extends JpaRepository<LoadEvent, UUID> {

    List<LoadEvent> findByLoadIdOrderByOccurredAtAsc(UUID loadId);

    List<LoadEvent> findByLoadIdAndEventType(UUID loadId, String eventType);
}
