package com.company.logicstic.repository;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.CalculationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalculationSnapshotRepository extends JpaRepository<CalculationSnapshot, UUID> {

    List<CalculationSnapshot> findByEntityTypeAndEntityIdOrderByCalculatedAtDesc(String entityType, UUID entityId);

    List<CalculationSnapshot> findByCorrelationId(String correlationId);
}
