package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverDependent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverDependentRepository extends JpaRepository<DriverDependent, UUID> {
    @EntityGraph(attributePaths = "employee")
    Page<DriverDependent> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Optional<DriverDependent> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    boolean existsByEmployee_Id(UUID employeeId);
}
