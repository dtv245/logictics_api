package com.company.logicstic.service;

import com.company.logicstic.dto.payment.CreatePaymentRequest;
import com.company.logicstic.dto.payment.PaymentView;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.Payment;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.payment.UpdatePaymentRequest;
import com.company.logicstic.repository.PaymentCommandRepository;
import com.company.logicstic.service.rating.RatingFingerprintService;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.mapper.PaymentMapper;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentIntegrityTest {

    private PaymentRepository paymentRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentMapper paymentMapper;
    private CurrentUserService currentUserService;
    private PaymentService paymentService;
    private PaymentCommandRepository commands;

    private UUID invoiceId;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentMapper = Mappers.getMapper(PaymentMapper.class);
        currentUserService = mock(CurrentUserService.class);

        commands = mock(PaymentCommandRepository.class);
        when(currentUserService.requireMappedEmployee()).thenReturn(new CurrentUserResponse("subject","test@example.test","tenant",List.of("ACCOUNTANT"),UUID.randomUUID()));
        paymentService = new PaymentService(
                paymentRepository,
                invoiceRepository,
                paymentMapper,
                currentUserService, commands, new RatingFingerprintService(tools.jackson.databind.json.JsonMapper.builder().build())
        );

        invoiceId = UUID.randomUUID();
        invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTotalAmount(new BigDecimal("1000.00"));
        invoice.setTotalCurrency("USD");
        invoice.setStatus("ISSUED");

        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
    }

    private CreatePaymentRequest sampleRequest(String key, BigDecimal amount, String currency) {
        return new CreatePaymentRequest(
                "PENDING",
                invoiceId,
                amount,
                currency,
                "Freight payment",
                "REF-001",
                key,
                "pm_123",
                "pi_123",
                null,
                "123 Street",
                null,
                "City",
                "State",
                "12345",
                "USA"
        );
    }

    @Test
    void createPaymentSucceedsAndPersistsRecord() {
        CreatePaymentRequest req = sampleRequest("KEY-001", new BigDecimal("500.00"), "USD");
        when(paymentRepository.findByIdempotencyKey("KEY-001")).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentView view = paymentService.create(req);
        assertNotNull(view);
        assertEquals(new BigDecimal("500.00"), view.amountAmount());
        verify(paymentRepository, times(1)).saveAndFlush(any(Payment.class));
    }

    @Test
    void idempotentReplayReturnsExistingPaymentWithoutSavingAgain() {
        CreatePaymentRequest req = sampleRequest("KEY-REPLAY", new BigDecimal("500.00"), "USD");

        Payment existing = paymentMapper.toEntity(req);
        existing.setId(UUID.randomUUID());
        existing.setInputHash(computeExpectedHash(req));
        existing.setIdempotencyKey("KEY-REPLAY");

        when(paymentRepository.findByIdempotencyKey("KEY-REPLAY")).thenReturn(Optional.of(existing));

        PaymentView view = paymentService.create(req);
        assertNotNull(view);
        assertEquals(existing.getId(), view.id());
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void conflictingPayloadWithSameKeyThrowsConflictException() {
        CreatePaymentRequest req1 = sampleRequest("KEY-CONFLICT", new BigDecimal("500.00"), "USD");
        CreatePaymentRequest req2 = sampleRequest("KEY-CONFLICT", new BigDecimal("600.00"), "USD");

        Payment existing = paymentMapper.toEntity(req1);
        existing.setId(UUID.randomUUID());
        existing.setInputHash(computeExpectedHash(req1));
        existing.setIdempotencyKey("KEY-CONFLICT");

        when(paymentRepository.findByIdempotencyKey("KEY-CONFLICT")).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class, () -> paymentService.create(req2));
        assertEquals("PAYMENT_IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    void paymentExceedingInvoiceBalanceThrowsBadRequestException() {
        CreatePaymentRequest req = sampleRequest("KEY-OVERPAY", new BigDecimal("1500.00"), "USD");
        when(paymentRepository.findByIdempotencyKey("KEY-OVERPAY")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> paymentService.create(req));
        assertEquals("PAYMENT_EXCEEDS_INVOICE_BALANCE", ex.getCode());
        assertEquals(422, ex.getStatus().value());
    }

    @Test
    void currencyMismatchThrowsCurrencyMismatchException() {
        CreatePaymentRequest req = sampleRequest("KEY-CURRENCY", new BigDecimal("500.00"), "EUR");
        when(paymentRepository.findByIdempotencyKey("KEY-CURRENCY")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> paymentService.create(req));
        assertEquals("CURRENCY_MISMATCH", ex.getCode());
        assertEquals(422, ex.getStatus().value());
    }

    @Test
    void physicalDeleteIsForbidden() {
        UUID paymentId = UUID.randomUUID();
        ApiException ex = assertThrows(ApiException.class, () -> paymentService.delete(paymentId));
        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("Physical deletion of financial payment records is forbidden"));
    }

    @Test
    void modifyingAmountDuringUpdateIsForbidden() {
        UUID paymentId = UUID.randomUUID();
        Payment existing = new Payment();
        existing.setId(paymentId);
        existing.setAmountAmount(new BigDecimal("500.00"));
        existing.setAmountCurrency("USD");

        when(commands.invoiceId(paymentId)).thenReturn(invoiceId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existing));

        UpdatePaymentRequest updateReq = new UpdatePaymentRequest(); updateReq.unsupportedField("amountAmount", new BigDecimal("600.00"));
        ApiException ex = assertThrows(ApiException.class, () -> paymentService.update(paymentId, updateReq));
        assertEquals("PAYMENT_FINANCIAL_FIELDS_IMMUTABLE", ex.getCode());
    }

    @Test
    void cancellingPaymentTransitionsToCancelled() {
        UUID paymentId = UUID.randomUUID();
        Payment existing = new Payment();
        existing.setId(paymentId);
        existing.setStatus("PENDING");
        existing.setAmountAmount(new BigDecimal("500.00"));
        existing.setAmountCurrency("USD");

        when(commands.invoiceId(paymentId)).thenReturn(invoiceId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existing));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentView view = paymentService.cancel(paymentId, "Customer request");
        assertNotNull(view);
        assertEquals("CANCELLED", view.status());
    }

    @Test
    void cancellingSettledPaymentThrowsConflict() {
        UUID paymentId = UUID.randomUUID();
        Payment existing = new Payment();
        existing.setId(paymentId);
        existing.setStatus("SETTLED");

        when(commands.invoiceId(paymentId)).thenReturn(invoiceId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existing));

        assertThrows(ApiException.class, () -> paymentService.cancel(paymentId, "Mistake"));
    }

    private String computeExpectedHash(CreatePaymentRequest req) {
        String raw = (req.invoiceId() != null ? req.invoiceId().toString() : "") + "|"
                + (req.amountAmount() != null ? req.amountAmount().stripTrailingZeros().toPlainString() : "") + "|"
                + (req.amountCurrency() != null ? req.amountCurrency().trim().toUpperCase() : "");
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
