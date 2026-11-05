package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverHosStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DriverHosStatusRepository extends JpaRepository<DriverHosStatus, UUID> {
    Optional<DriverHosStatus> findByEmployee_Id(UUID employeeId);
}
