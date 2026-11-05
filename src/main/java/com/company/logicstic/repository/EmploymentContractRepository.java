package com.company.logicstic.repository;

import com.company.logicstic.entity.EmploymentContract;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
    @EntityGraph(attributePaths = {"employee", "document"})
    Page<EmploymentContract> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "document"})
    Optional<EmploymentContract> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    boolean existsByContractNumberIgnoreCase(String contractNumber);
    boolean existsByContractNumberIgnoreCaseAndIdNot(String contractNumber, UUID id);
    boolean existsByEmployee_Id(UUID employeeId);
}
