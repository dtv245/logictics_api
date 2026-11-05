package com.company.logicstic.repository;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Paged driver directory query that can filter the optional operational status without loading rows individually. */
public interface DriverDirectoryRepository extends Repository<Employee, UUID> {
    @Query(value = """
            SELECT e FROM Employee e
            LEFT JOIN DriverOperationalStatus operational ON operational.employee.id = e.id
            WHERE e.employeeType = :employeeType
              AND (:search = ''
                   OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:employeeStatus IS NULL OR e.status = :employeeStatus)
              AND operational.availability = :availability
            """,
            countQuery = """
            SELECT COUNT(e) FROM Employee e
            LEFT JOIN DriverOperationalStatus operational ON operational.employee.id = e.id
            WHERE e.role.normalizedName = 'DRIVER'
              AND (:search = ''
                   OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:employeeStatus IS NULL OR e.status = :employeeStatus)
              AND operational.availability = :availability
            """)
    Page<Employee> searchByAvailability(@Param("employeeType") EmployeeType employeeType,
            @Param("search") String search,
            @Param("employeeStatus") String employeeStatus,
            @Param("availability") String availability,
            Pageable pageable);
}
