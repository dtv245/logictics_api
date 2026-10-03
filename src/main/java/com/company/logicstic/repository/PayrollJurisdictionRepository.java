package com.company.logicstic.repository;
import com.company.logicstic.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
import com.company.logicstic.service.payroll.domain.WorkerClassification;
public interface PayrollJurisdictionRepository extends JpaRepository<PayrollJurisdictionEntity,UUID> {
 Optional<PayrollJurisdictionEntity> findByCountryCodeAndSubdivisionCodeAndLocalityCode(String c,String s,String l);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT j FROM PayrollJurisdictionEntity j WHERE j.id=:id")
 Optional<PayrollJurisdictionEntity> findByIdForUpdate(@Param("id") UUID id);
}
