package com.company.logicstic.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp="(?i)PENDING") String status,
        @NotNull UUID invoiceId,
        @NotNull @jakarta.validation.constraints.DecimalMin(value="0",inclusive=false)
        @jakarta.validation.constraints.Digits(integer=16,fraction=2) BigDecimal amountAmount,
        @NotBlank String amountCurrency,
        String description,
        String referenceNumber,
        @NotBlank @jakarta.validation.constraints.Size(max=200) String idempotencyKey,
        String stripePaymentMethodId,
        String stripePaymentIntentId,
        @jakarta.validation.constraints.Null OffsetDateTime recordedAt,
        // Billing address
        @NotBlank String billingAddressLine1,
        String billingAddressLine2,
        @NotBlank String billingAddressCity,
        @NotBlank String billingAddressState,
        @NotBlank String billingAddressZipCode,
        @NotBlank String billingAddressCountry
) {}
