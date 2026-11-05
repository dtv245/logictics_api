package com.company.logicstic.dto.cost;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectExpenseRequest(
        @NotBlank @Size(max = 500) String reason
) {}
