package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverEmergencyContact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverEmergencyContactRepository extends JpaRepository<DriverEmergencyContact, UUID> {
    @EntityGraph(attributePaths = "employee")
    Page<DriverEmergencyContact> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<DriverEmergencyContact> findByEmployee_IdAndActiveTrue(UUID employeeId, Pageable pageable);

    Optional<DriverEmergencyContact> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    Optional<DriverEmergencyContact> findFirstByEmployee_IdAndPrimaryContactTrueAndActiveTrue(UUID employeeId);
}
