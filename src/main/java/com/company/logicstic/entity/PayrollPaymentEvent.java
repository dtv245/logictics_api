package com.company.logicstic.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="payroll_payment_events") @Getter @Setter @NoArgsConstructor
public class PayrollPaymentEvent {
 @Id @GeneratedValue @UuidGenerator @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id",nullable=false) private PayrollPayment payment;
 @Column(name="source_type",nullable=false,length=30) private String sourceType;
 @Column(name="source_key",nullable=false,length=200) private String sourceKey;
 @Column(name="provider_key",length=100) private String providerKey;
 @Column(name="actor_id") private UUID actorId;
 @Column(nullable=false,length=30) private String outcome;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="provider_reference",length=200) private String providerReference;
 @Column(name="occurred_at",nullable=false) private OffsetDateTime occurredAt;
 @Column(nullable=false,length=40) private String status;
 @Column(length=300) private String reason;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="payload_json",nullable=false,columnDefinition="jsonb") private String payloadJson;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="verification_json",nullable=false,columnDefinition="jsonb") private String verificationJson;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="resolves_event_id") private PayrollPaymentEvent resolvesEvent;
}
