package com.company.logicstic.repository;
import com.company.logicstic.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
import com.company.logicstic.service.payroll.domain.WorkerClassification;
public interface PayrollPolicyVersionRepository extends JpaRepository<PayrollPolicyVersion,UUID> {
 Optional<PayrollPolicyVersion> findTopByPolicyCodeOrderByPolicyVersionDesc(String code);
 List<PayrollPolicyVersion> findByJurisdictionIdAndWorkerClassificationAndEffectiveFromLessThanEqualOrderByEffectiveFromDescPolicyVersionDesc(UUID jurisdiction,WorkerClassification classification,LocalDate date);
}
