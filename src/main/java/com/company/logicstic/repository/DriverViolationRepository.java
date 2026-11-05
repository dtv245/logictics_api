package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverViolation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverViolationRepository extends JpaRepository<DriverViolation, UUID> {
    @EntityGraph(attributePaths = {"employee", "trip", "document"})
    Page<DriverViolation> findByEmployee_IdAndActiveTrue(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "trip", "document"})
    Optional<DriverViolation> findByIdAndEmployee_Id(UUID id, UUID employeeId);
}
