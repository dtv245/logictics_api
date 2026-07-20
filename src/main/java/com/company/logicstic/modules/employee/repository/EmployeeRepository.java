package com.company.logicstic.modules.employee.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.logicstic.modules.employee.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"role"})
    @Query("""
            SELECT e FROM Employee e
            WHERE (:search IS NULL
                   OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:roleId IS NULL OR e.role.id = :roleId)
            """)
    Page<Employee> search(
            @Param("search") String search,
            @Param("status") String status,
            @Param("roleId") UUID roleId,
            Pageable pageable
    );
}
