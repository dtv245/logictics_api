package com.company.logicstic.repository;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.AccessorialCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessorialChargeRepository extends JpaRepository<AccessorialCharge, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM AccessorialCharge a WHERE a.id = :id")
    java.util.Optional<AccessorialCharge> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<AccessorialCharge> findByLoadId(UUID loadId);

    List<AccessorialCharge> findByTripId(UUID tripId);

    List<AccessorialCharge> findByTripStopId(UUID tripStopId);

    List<AccessorialCharge> findByStatus(String status);
}
