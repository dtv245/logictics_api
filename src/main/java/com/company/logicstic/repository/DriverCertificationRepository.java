package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverCertification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverCertificationRepository extends JpaRepository<DriverCertification, UUID> {
    @EntityGraph(attributePaths = {"employee", "document"})
    Page<DriverCertification> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "document"})
    Optional<DriverCertification> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    boolean existsByEmployee_Id(UUID employeeId);
}
