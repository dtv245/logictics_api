package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverSettlementRepository extends JpaRepository<DriverSettlement, UUID> {
    Optional<DriverSettlement> findByDriverIdAndPayPeriodIdAndSettlementType(UUID driverId, UUID payPeriodId, String settlementType);
    List<DriverSettlement> findByPayPeriodIdAndStatus(UUID payPeriodId, String status);
    Optional<DriverSettlement> findByParentSettlementIdAndRequestKey(UUID parentId, String requestKey);
    Optional<DriverSettlement> findByParentSettlementIdAndSettlementType(UUID parentId, String type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DriverSettlement s WHERE s.id = :id")
    Optional<DriverSettlement> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(s.sequenceNumber), 0) FROM DriverSettlement s WHERE s.parentSettlement.id = :parentId")
    Integer findMaxSequenceForParent(@Param("parentId") UUID parentId);
}
