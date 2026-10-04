package com.company.logicstic.repository;
import com.company.logicstic.entity.PayrollPaymentEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface PayrollPaymentEventRepository extends JpaRepository<PayrollPaymentEvent,UUID> {
 Optional<PayrollPaymentEvent> findBySourceTypeAndProviderKeyAndSourceKey(String source,String provider,String key);
 @Query("SELECT COUNT(e)>0 FROM PayrollPaymentEvent e WHERE e.resolvesEvent.id=:eventId AND e.status=:status")
 boolean hasAppliedResolution(@Param("eventId") UUID eventId,@Param("status") String status);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT e FROM PayrollPaymentEvent e WHERE e.id=:id")
 Optional<PayrollPaymentEvent> findByIdForUpdate(@Param("id") UUID id);
 @Query("SELECT e FROM PayrollPaymentEvent e WHERE e.status='RECONCILIATION_REQUIRED' AND NOT EXISTS(SELECT r FROM PayrollPaymentEvent r WHERE r.resolvesEvent=e AND r.status='APPLIED') ORDER BY e.occurredAt,e.id")
 Page<PayrollPaymentEvent> findOpenReconciliationCases(Pageable pageable);
 @Query(value="SELECT id FROM payroll_payment_events WHERE source_type='BANK' AND status='APPLIED' AND verification_json->>'transactionReference'=:reference",nativeQuery=true)
 Optional<UUID> findAppliedBankTransactionId(@Param("reference") String reference);
 @Query("SELECT COUNT(e)>0 FROM PayrollPaymentEvent e WHERE e.payment.item.id=:item AND e.status='RECONCILIATION_REQUIRED' AND NOT EXISTS(SELECT r FROM PayrollPaymentEvent r WHERE r.resolvesEvent=e AND r.status='APPLIED')")
 boolean hasUnresolvedCase(@Param("item") UUID item);
}
