package com.company.logicstic.repository;

import com.company.logicstic.entity.PayPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PayPeriodRepository extends JpaRepository<PayPeriod, UUID> {
    boolean existsByPeriodCode(String code);
    java.util.List<PayPeriod> findAllByOrderByStartDateDesc();
}
