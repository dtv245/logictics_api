package com.company.logicstic.repository;

import com.company.logicstic.entity.Payment;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class PaymentCommandRepository {
    private final JdbcTemplate jdbc;
    public void lockKey(String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs->{}, "PAYMENT_CREATE:"+key);
    }
    /** Scalar lookup avoids loading stale payment state before locking its invoice. */
    public UUID invoiceId(UUID paymentId) {
        var rows=jdbc.query("select invoice_id from payments where id=?", (rs,n)->rs.getObject(1,UUID.class),paymentId);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Payment not found: "+paymentId);
        if(rows.getFirst()==null) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_INVOICE_UNRESOLVED","Legacy payment has no invoice consistency boundary");
        return rows.getFirst();
    }
    public void audit(Payment payment,String command,String oldStatus,String oldDescription,String oldReference,UUID actor,String reason) {
        jdbc.update("""
                insert into payment_command_events(id,payment_id,payment_version,command_type,previous_status,new_status,
                previous_description,new_description,previous_reference_number,new_reference_number,actor_id,reason)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),payment.getId(),payment.getVersion(),command,oldStatus,payment.getStatus(),oldDescription,payment.getDescription(),
                oldReference,payment.getReferenceNumber(),actor,reason);
    }
}
