package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateDriverInsuranceRequest(
        @Pattern(regexp = "[A-Za-z0-9 -]{8,20}|\\*{4}[A-Za-z0-9]{4}") String socialInsuranceNumber,
        @Pattern(regexp = "[A-Za-z0-9 -]{8,20}|\\*{4}[A-Za-z0-9]{4}") String healthInsuranceCardNumber,
        @Size(max = 255) String registeredClinic,
        @NotNull LocalDate participationStartDate,
        LocalDate participationEndDate,
        @Size(max = 32) String socialInsuranceStatus,
        @Size(max = 32) String healthInsuranceStatus,
        @Size(max = 32) String unemploymentInsuranceStatus,
        @DecimalMin("0.00") BigDecimal insuredSalary,
        LocalDate healthInsuranceExpiryDate,
        @Size(max = 1000) String note,
        @NotNull Boolean active
) {
}
