package com.company.logicstic.repository;
import com.company.logicstic.entity.PayrollSupplement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PayrollSupplementRepository extends JpaRepository<PayrollSupplement,UUID> {}
