package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverBankAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverBankAccountRepository extends JpaRepository<DriverBankAccount, UUID> {
    @EntityGraph(attributePaths = "employee")
    Page<DriverBankAccount> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Optional<DriverBankAccount> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    Optional<DriverBankAccount> findFirstByEmployee_IdAndPrimaryTrueAndActiveTrue(UUID employeeId);

    boolean existsByEmployee_Id(UUID employeeId);
}
