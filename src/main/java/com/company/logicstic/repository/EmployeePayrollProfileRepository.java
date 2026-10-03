package com.company.logicstic.repository;
import com.company.logicstic.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
import com.company.logicstic.service.payroll.domain.WorkerClassification;
public interface EmployeePayrollProfileRepository extends JpaRepository<EmployeePayrollProfile,UUID> {
 List<EmployeePayrollProfile> findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescProfileVersionDesc(UUID employee,LocalDate date);
 Optional<EmployeePayrollProfile> findTopByEmployeeIdOrderByProfileVersionDesc(UUID employee);
}
