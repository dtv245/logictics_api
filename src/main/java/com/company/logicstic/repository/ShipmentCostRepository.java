package com.company.logicstic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.entity.ShipmentCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentCostRepository extends JpaRepository<ShipmentCost, UUID> {
    java.util.List<ShipmentCost> findByTripIdAndLoadIsNull(UUID tripId);

    List<ShipmentCost> findByLoadId(UUID loadId);

    List<ShipmentCost> findByLoadIdAndCostBasisAndStatusIn(UUID loadId, String costBasis, Collection<String> statuses);

    Optional<ShipmentCost> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    boolean existsBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    List<ShipmentCost> findByTripId(UUID tripId);

    List<ShipmentCost> findByTruckId(UUID truckId);
}
