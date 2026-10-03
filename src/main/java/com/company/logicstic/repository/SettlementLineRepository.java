package com.company.logicstic.repository;

import com.company.logicstic.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, UUID> {
    List<SettlementLine> findBySettlementIdOrderById(UUID settlementId);
    @org.springframework.data.jpa.repository.Query("SELECT l FROM SettlementLine l WHERE l.settlement.driver.id=:driver AND l.settlement.settlementType='ORIGINAL' AND l.settlement.status <> 'CANCELLED'")
    List<SettlementLine> findOriginalLinesByDriver(@org.springframework.data.repository.query.Param("driver") UUID driver);
}
