package com.company.logicstic.service;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.payment.*;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.Payment;
import com.company.logicstic.exception.*;
import com.company.logicstic.mapper.PaymentMapper;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;
    private final CurrentUserService currentUserService;
    private final PaymentCommandRepository commands;
    private final RatingFingerprintService fingerprints;

    public PagedResponse<PaymentView> search(String status,UUID invoiceId,int page,int pageSize,String orderBy,boolean descending) {
        var sort=descending?Sort.by(orderBy).descending():Sort.by(orderBy).ascending();
        return PagedResponse.from(paymentRepository.search(status,invoiceId,PageRequest.of(page-1,pageSize,sort)).map(paymentMapper::toView));
    }
    public PaymentView getById(UUID id) {
        return paymentRepository.findById(id).map(paymentMapper::toView).orElseThrow(()->new ResourceNotFoundException("Payment not found: "+id));
    }
    @Transactional
    public PaymentView create(CreatePaymentRequest request) {
        UUID actor=actor().employeeId();
        validateCreate(request);
        String key=request.idempotencyKey().trim(),currency=CurrencyGuard.normalize(request.amountCurrency());
        String hash=computeInputHash(request);
        commands.lockKey(key);
        var prior=paymentRepository.findByIdempotencyKey(key);
        if(prior.isPresent()) {
            Payment payment=prior.get();state(payment.getStatus());
            String expected=payment.getInputHashVersion()==null?legacyHash(request):hash;
            if(!Objects.equals(expected,payment.getInputHash()))
                throw conflict("PAYMENT_IDEMPOTENCY_CONFLICT","Payment key was already used with different creation input");
            return paymentMapper.toView(payment);
        }
        Invoice invoice=invoiceRepository.findByIdForUpdate(request.invoiceId())
                .orElseThrow(()->new ResourceNotFoundException("Invoice not found: "+request.invoiceId()));
        if(!Set.of("ISSUED","SENT","PARTIALLY_PAID").contains(normalize(invoice.getStatus())) || invoice.economicSign()<0
                || "CREDIT".equalsIgnoreCase(invoice.getInvoicePurpose()))
            throw conflict("PAYMENT_INVOICE_NOT_COLLECTIBLE","Invoice is not eligible to receive a new payment");
        requirePaymentCurrency(invoice.getTotalCurrency(),currency);
        BigDecimal reserved=BigDecimal.ZERO;
        for(Payment existing:paymentRepository.findByInvoiceId(invoice.getId())) {
            String status=state(existing.getStatus());
            if(!Set.of("CANCELLED","VOID").contains(status)) {
                requirePaymentCurrency(currency,existing.getAmountCurrency());
                if(existing.getAmountAmount()==null || existing.getAmountAmount().signum()<=0)
                    throw conflict("PAYMENT_LEGACY_STATE_UNRESOLVED","Invoice payment history contains an invalid amount");
                reserved=reserved.add(existing.getAmountAmount());
            }
        }
        if(invoice.getTotalAmount()==null || request.amountAmount().compareTo(invoice.getTotalAmount().subtract(reserved))>0)
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,"PAYMENT_EXCEEDS_INVOICE_BALANCE","Payment exceeds remaining invoice balance");
        Payment payment=paymentMapper.toEntity(request);
        payment.setStatus("PENDING");payment.setAmountCurrency(currency);payment.setInvoice(invoice);
        payment.setIdempotencyKey(key);payment.setInputHash(hash);payment.setInputHashVersion((short)1);
        // Opaque legacy field retained. Physical datasource routing remains the tenant boundary.
        payment.setTenantId(UUID.randomUUID());payment.setRecordedByUserId(actor);payment.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payment=paymentRepository.saveAndFlush(payment);
        commands.audit(payment,"CREATE",null,null,null,actor,null);
        return paymentMapper.toView(payment);
    }
    @Transactional
    public PaymentView update(UUID id,UpdatePaymentRequest request) {
        UUID actor=actor().employeeId();
        if(request==null)throw new BadRequestException("PAYMENT_METADATA_REQUIRED","Metadata request required");
        if(request.hasUnsupportedFields())throw conflict("PAYMENT_FINANCIAL_FIELDS_IMMUTABLE","Only description and referenceNumber may be supplied");
        Payment payment=lockedPayment(id);
        if(!"PENDING".equals(state(payment.getStatus())))throw conflict("PAYMENT_METADATA_STATE_CONFLICT","Only pending payment metadata may be edited");
        String description=payment.getDescription(),reference=payment.getReferenceNumber();
        if(request.hasDescription())payment.setDescription(request.getDescription());
        if(request.hasReferenceNumber())payment.setReferenceNumber(request.getReferenceNumber());
        if(Objects.equals(description,payment.getDescription()) && Objects.equals(reference,payment.getReferenceNumber()))return paymentMapper.toView(payment);
        payment=paymentRepository.saveAndFlush(payment);
        commands.audit(payment,"METADATA",payment.getStatus(),description,reference,actor,null);
        return paymentMapper.toView(payment);
    }
    @Transactional
    public PaymentView cancel(UUID id,String reason) {
        UUID actor=actor().employeeId();
        if(reason==null || reason.isBlank() || reason.length()>1000)throw new BadRequestException("PAYMENT_CANCEL_REASON_REQUIRED","Cancellation requires a reason of at most 1000 characters");
        Payment payment=lockedPayment(id);String status=state(payment.getStatus());
        if("CANCELLED".equals(status))return paymentMapper.toView(payment);
        if(!"PENDING".equals(status))throw conflict("PAYMENT_CANCEL_STATE_CONFLICT","Only pending payments may be cancelled; settled reversal is a separate workflow");
        String oldStatus=payment.getStatus();payment.setStatus("CANCELLED");
        payment=paymentRepository.saveAndFlush(payment);
        commands.audit(payment,"CANCEL",oldStatus,payment.getDescription(),payment.getReferenceNumber(),actor,reason.trim());
        return paymentMapper.toView(payment);
    }
    @Transactional
    public void delete(UUID id) {
        actor();throw conflict("PAYMENT_DELETE_FORBIDDEN","Physical deletion of financial payment records is forbidden. Use cancellation instead.");
    }
    private Payment lockedPayment(UUID id) {
        UUID invoice=commands.invoiceId(id);
        invoiceRepository.findByIdForUpdate(invoice).orElseThrow(()->new ResourceNotFoundException("Invoice not found: "+invoice));
        return paymentRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Payment not found: "+id));
    }
    private CurrentUserResponse actor() {
        var actor=currentUserService.requireMappedEmployee();
        if(actor.roles().stream().noneMatch(role->Set.of("ADMIN","ACCOUNTANT").contains(role)))throw new ForbiddenException("Authorized accounting employee required");
        return actor;
    }
    private void validateCreate(CreatePaymentRequest r) {
        if(r==null || r.invoiceId()==null || r.idempotencyKey()==null || r.idempotencyKey().isBlank() || r.idempotencyKey().length()>200)
            throw new BadRequestException("PAYMENT_COMMAND_INVALID","Invoice and a nonblank idempotency key of at most 200 characters are required");
        if(!"PENDING".equals(normalize(r.status())) || r.recordedAt()!=null)
            throw new BadRequestException("PAYMENT_CREATION_STATE_INVALID","Create requires PENDING; recorded identity and time are assigned by the server");
        if(r.amountAmount()==null || r.amountAmount().signum()<=0 || r.amountAmount().scale()>2 || r.amountAmount().precision()-r.amountAmount().scale()>16)
            throw new BadRequestException("PAYMENT_AMOUNT_INVALID","A positive amount exactly representable as NUMERIC(18,2) is required");
        String currency=CurrencyGuard.normalize(r.amountCurrency());
        try {if(Currency.getInstance(currency).getDefaultFractionDigits()<0)throw new IllegalArgumentException();}
        catch(IllegalArgumentException invalid){throw new BadRequestException("PAYMENT_CURRENCY_UNSUPPORTED","Supported monetary currency required");}
    }
    private String computeInputHash(CreatePaymentRequest r) {
        Map<String,Object> values=new TreeMap<>();
        values.put("format","PAYMENT_CREATE_V1");values.put("status","PENDING");values.put("invoiceId",r.invoiceId());
        values.put("amount",r.amountAmount());values.put("currency",CurrencyGuard.normalize(r.amountCurrency()));
        values.put("description",r.description());values.put("referenceNumber",r.referenceNumber());
        values.put("stripePaymentMethodId",r.stripePaymentMethodId());values.put("stripePaymentIntentId",r.stripePaymentIntentId());
        values.put("billingAddressLine1",r.billingAddressLine1());values.put("billingAddressLine2",r.billingAddressLine2());
        values.put("billingAddressCity",r.billingAddressCity());values.put("billingAddressState",r.billingAddressState());
        values.put("billingAddressZipCode",r.billingAddressZipCode());values.put("billingAddressCountry",r.billingAddressCountry());
        return fingerprints.hash(values);
    }
    private void requirePaymentCurrency(String expected, String actual) {
        try {
            CurrencyGuard.requireSameCurrency(expected, actual);
        } catch (CurrencyMismatchException mismatch) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, mismatch.getCode(), mismatch.getMessage());
        }
    }
    private String legacyHash(CreatePaymentRequest r) {
        String raw=r.invoiceId()+"|"+r.amountAmount().stripTrailingZeros().toPlainString()+"|"+CurrencyGuard.normalize(r.amountCurrency());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private String state(String value) {
        String status=normalize(value);
        if(!Set.of("PENDING","COMPLETED","PAID","SUCCEEDED","SETTLED","CANCELLED","VOID").contains(status))
            throw conflict("PAYMENT_LEGACY_STATE_UNRESOLVED","Payment history contains an unresolved status");
        return status;
    }
    private String normalize(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
