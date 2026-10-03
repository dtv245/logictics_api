package com.company.logicstic.repository;
import com.company.logicstic.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
import com.company.logicstic.service.payroll.domain.WorkerClassification;
public interface PayrollRunItemRepository extends JpaRepository<PayrollRunItem,UUID> {
 List<PayrollRunItem> findByPayrollRunIdOrderById(UUID id);
 boolean existsBySettlementsId(UUID settlementId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT i FROM PayrollRunItem i WHERE i.id=:id")
 Optional<PayrollRunItem> findByIdForUpdate(@Param("id") UUID id);
}
