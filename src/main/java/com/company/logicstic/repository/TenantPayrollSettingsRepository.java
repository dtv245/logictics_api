package com.company.logicstic.repository;
import com.company.logicstic.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
import com.company.logicstic.service.payroll.domain.WorkerClassification;
public interface TenantPayrollSettingsRepository extends JpaRepository<TenantPayrollSettings,Integer> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT s FROM TenantPayrollSettings s WHERE s.id=1")
 Optional<TenantPayrollSettings> lockSettings();
}
