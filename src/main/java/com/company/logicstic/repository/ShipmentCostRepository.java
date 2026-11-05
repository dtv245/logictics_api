package com.company.logicstic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.entity.ShipmentCost;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentCostRepository extends JpaRepository<ShipmentCost, UUID> {
    java.util.List<ShipmentCost> findByTripIdAndLoadIsNull(UUID tripId);

    List<ShipmentCost> findByLoadId(UUID loadId);

    @EntityGraph(attributePaths = {"load", "trip", "truck", "driver"})
    Page<ShipmentCost> findPageByLoadId(UUID loadId, Pageable pageable);

    List<ShipmentCost> findByLoadIdAndCostBasisAndStatusIn(UUID loadId, String costBasis, Collection<String> statuses);

    Optional<ShipmentCost> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    boolean existsBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    List<ShipmentCost> findByTripId(UUID tripId);

    List<ShipmentCost> findByTruckId(UUID truckId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShipmentCost c where c.id = :id")
    Optional<ShipmentCost> findByIdForUpdate(@Param("id") UUID id);
}
