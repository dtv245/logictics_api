package com.company.logicstic.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Expense e WHERE e.id = :id")
    Optional<Expense> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT e FROM Expense e
            WHERE e.employee.id = :employeeId AND LOWER(e.status) = 'approved'
              AND e.expenseDate >= :from AND e.expenseDate < :to
            ORDER BY e.expenseDate
            """)
    List<Expense> findApprovedByEmployeeAndPeriod(@Param("employeeId") UUID employeeId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    List<Expense> findByLoadId(UUID loadId);

    @Query("""
            SELECT e FROM Expense e
            WHERE (:status IS NULL OR LOWER(e.status) = LOWER(:status))
              AND (:truckId IS NULL OR e.truck.id = :truckId)
              AND (:from IS NULL OR e.expenseDate >= :from)
              AND (:to IS NULL OR e.expenseDate <= :to)
            """)
    List<Expense> findByFilters(
            @Param("status") String status,
            @Param("truckId") UUID truckId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
            SELECT e FROM Expense e
            WHERE LOWER(e.status) = 'approved'
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:from IS NULL OR e.expenseDate >= :from)
              AND (:to IS NULL OR e.expenseDate <= :to)
            """)
    List<Expense> findApprovedExpenses(
            @Param("category") String category,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
