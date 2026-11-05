package com.company.logicstic.repository;
import com.company.logicstic.entity.PayrollPayment;
import com.company.logicstic.dto.payroll.PayrollPaymentView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment,UUID> {
 @Query("SELECT p.item.payrollRun.id FROM PayrollPayment p WHERE p.id=:id")
 Optional<UUID> findPayrollRunId(@Param("id") UUID id);
 Optional<PayrollPayment> findByIdempotencyKey(String key);
 List<PayrollPayment> findByItemIdOrderByAttemptNumberAsc(UUID item);
 @Query("""
     select new com.company.logicstic.dto.payroll.PayrollPaymentView(
       p.id, p.item.id, p.attemptNumber, p.idempotencyKey, p.paymentMethod, p.status,
       p.amount, p.currency, p.providerKey, p.providerReference, p.failureCode, p.failureMessage,
       p.scheduledAt, p.submittedAt, p.succeededAt, p.reconciledAt)
     from PayrollPayment p where p.item.id = :itemId
     """)
 Page<PayrollPaymentView> findPageByItemId(@Param("itemId") UUID itemId, Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT p FROM PayrollPayment p WHERE p.id=:id")
 Optional<PayrollPayment> findByIdForUpdate(@Param("id") UUID id);
}
