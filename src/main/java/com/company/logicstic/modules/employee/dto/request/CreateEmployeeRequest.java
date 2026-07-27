package com.company.logicstic.modules.employee.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateEmployeeRequest(
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phoneNumber,
    @NotBlank String salaryType,
    @NotBlank String status,
    @NotNull OffsetDateTime joinedDate,
    UUID roleId,
    @NotNull BigDecimal salaryAmount,
    @NotBlank String salaryCurrency,
    String addressLine1,
    String addressLine2,
    String addressCity,
    String addressState,
    String addressZipCode,
    String addressCountry) {}
