package com.company.logicstic.repository;
import com.company.logicstic.entity.PayrollPayment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment,UUID> {
 @Query("SELECT p.item.payrollRun.id FROM PayrollPayment p WHERE p.id=:id")
 Optional<UUID> findPayrollRunId(@Param("id") UUID id);
 Optional<PayrollPayment> findByIdempotencyKey(String key);
 List<PayrollPayment> findByItemIdOrderByAttemptNumberAsc(UUID item);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT p FROM PayrollPayment p WHERE p.id=:id")
 Optional<PayrollPayment> findByIdForUpdate(@Param("id") UUID id);
}
