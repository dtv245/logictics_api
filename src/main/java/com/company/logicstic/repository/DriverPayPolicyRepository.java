package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverPayPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverPayPolicyRepository extends JpaRepository<DriverPayPolicy, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM DriverPayPolicy p WHERE p.id = :id")
    Optional<DriverPayPolicy> findByIdForUpdate(@Param("id") UUID id);
    List<DriverPayPolicy> findAllByOrderByPolicyCodeAscPolicyVersionDesc();
    Optional<DriverPayPolicy> findTopByPolicyCodeOrderByPolicyVersionDesc(String policyCode);
    Optional<DriverPayPolicy> findFirstByActiveTrueAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByPolicyVersionDesc(LocalDate from, LocalDate to);
    Optional<DriverPayPolicy> findFirstByDriverIdAndActiveTrueAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByPolicyVersionDesc(UUID driverId, LocalDate from, LocalDate to);

    @Query("""
            SELECT p FROM DriverPayPolicy p
            WHERE p.active = true AND p.driver.id = :driverId
              AND p.effectiveFrom <= :workDate
            ORDER BY p.effectiveFrom DESC, p.policyVersion DESC
            """)
    List<DriverPayPolicy> findDriverPoliciesAt(@Param("driverId") UUID driverId, @Param("workDate") LocalDate workDate);

    @Query("""
            SELECT p FROM DriverPayPolicy p
            WHERE p.active = true AND p.driver IS NULL
              AND p.effectiveFrom <= :workDate
            ORDER BY p.effectiveFrom DESC, p.policyVersion DESC
            """)
    List<DriverPayPolicy> findDefaultPoliciesAt(@Param("workDate") LocalDate workDate);
}
