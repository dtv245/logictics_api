package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverHealthCheck;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverHealthCheckRepository extends JpaRepository<DriverHealthCheck, UUID> {
    @EntityGraph(attributePaths = {"employee", "document"})
    Page<DriverHealthCheck> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "document"})
    Optional<DriverHealthCheck> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    boolean existsByEmployee_Id(UUID employeeId);
}
