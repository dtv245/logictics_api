package com.company.logicstic.repository;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.LoadEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadEventRepository extends JpaRepository<LoadEvent, UUID> {

    List<LoadEvent> findByLoadIdOrderByOccurredAtAsc(UUID loadId);

    @EntityGraph(attributePaths = {"load", "trip", "tripStop"})
    Page<LoadEvent> findPageByLoadId(UUID loadId, Pageable pageable);

    List<LoadEvent> findByLoadIdAndEventType(UUID loadId, String eventType);
}
