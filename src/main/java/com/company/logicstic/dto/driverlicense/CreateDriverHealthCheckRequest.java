package com.company.logicstic.dto.driverlicense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDriverHealthCheckRequest(
        @NotNull @PastOrPresent LocalDate checkedDate,
        @NotNull LocalDate expiryDate,
        @NotBlank @Size(max = 255) String facility,
        @NotBlank @Pattern(regexp = "FIT|UNFIT") String conclusion,
        UUID documentId
) {
}
