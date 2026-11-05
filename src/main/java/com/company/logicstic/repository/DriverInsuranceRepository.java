package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverInsurance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverInsuranceRepository extends JpaRepository<DriverInsurance, UUID> {
    @EntityGraph(attributePaths = "employee")
    Page<DriverInsurance> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Optional<DriverInsurance> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    boolean existsByEmployee_Id(UUID employeeId);
}
