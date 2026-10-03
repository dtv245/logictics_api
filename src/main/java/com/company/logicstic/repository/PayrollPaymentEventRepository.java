package com.company.logicstic.repository;
import com.company.logicstic.entity.PayrollPaymentEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PayrollPaymentEventRepository extends JpaRepository<PayrollPaymentEvent,UUID> {
 Optional<PayrollPaymentEvent> findBySourceTypeAndProviderKeyAndSourceKey(String source,String provider,String key);
 @Query("SELECT COUNT(e)>0 FROM PayrollPaymentEvent e WHERE e.payment.item.id=:item AND e.status='RECONCILIATION_REQUIRED' AND NOT EXISTS(SELECT r FROM PayrollPaymentEvent r WHERE r.resolvesEvent=e AND r.status='APPLIED')")
 boolean hasUnresolvedCase(@Param("item") UUID item);
}
