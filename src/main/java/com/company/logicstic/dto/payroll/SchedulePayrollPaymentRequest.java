package com.company.logicstic.dto.payroll;
import jakarta.validation.constraints.*;
public record SchedulePayrollPaymentRequest(@NotBlank @Size(max=120) String idempotencyKey,@NotBlank String paymentMethod,
 @Size(max=100) String providerKey,@Size(max=200) String destinationReference) {}
