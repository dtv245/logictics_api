package com.company.logicstic.repository;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopRepository extends JpaRepository<TripStop, UUID> {

    List<TripStop> findByTripIdOrderByOrderAsc(UUID tripId);

    List<TripStop> findByLoadId(UUID loadId);

    List<TripStop> findByTripIdIn(List<UUID> tripIds);
}
