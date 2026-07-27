package com.company.logicstic.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreatePaymentRequest(
    @NotBlank String status,
    UUID invoiceId,
    @NotNull BigDecimal amountAmount,
    @NotBlank String amountCurrency,
    String description,
    String referenceNumber,
    String stripePaymentMethodId,
    String stripePaymentIntentId,
    OffsetDateTime recordedAt,
    // Billing address
    @NotBlank String billingAddressLine1,
    String billingAddressLine2,
    @NotBlank String billingAddressCity,
    @NotBlank String billingAddressState,
    @NotBlank String billingAddressZipCode,
    @NotBlank String billingAddressCountry) {}
