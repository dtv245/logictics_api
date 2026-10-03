package com.company.logicstic.repository;

import com.company.logicstic.entity.PayPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PayPeriodRepository extends JpaRepository<PayPeriod, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PayPeriod p WHERE p.id = :id")
    java.util.Optional<PayPeriod> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    boolean existsByPeriodCode(String code);
    java.util.List<PayPeriod> findAllByOrderByStartDateDesc();
}
