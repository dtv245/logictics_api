package com.company.logicstic.dto.load;

/** Evidence supplied with an intentional business-date command, never inferred from an instant. */
public record PickupBusinessDateProvenance(@jakarta.validation.constraints.NotBlank String reasonCode, @jakarta.validation.constraints.NotBlank String reason, @jakarta.validation.constraints.NotBlank String source) { }
