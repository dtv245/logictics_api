package com.company.logicstic.repository;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.TripStop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface TripStopRepository extends JpaRepository<TripStop, UUID> {

    List<TripStop> findByTripIdOrderByOrderAsc(UUID tripId);

    @EntityGraph(attributePaths = {"trip", "load"})
    Page<TripStop> findPageByTripId(UUID tripId, Pageable pageable);

    List<TripStop> findByLoadId(UUID loadId);

    List<TripStop> findByTripIdIn(List<UUID> tripIds);
}
