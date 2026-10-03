package com.company.logicstic.repository;
import com.company.logicstic.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
import com.company.logicstic.service.payroll.domain.WorkerClassification;
public interface PayrollRunRepository extends JpaRepository<PayrollRun,UUID> {
 Optional<PayrollRun> findByRequestKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT r FROM PayrollRun r WHERE r.id=:id")
 Optional<PayrollRun> findByIdForUpdate(@Param("id") UUID id);
}
