package com.company.logicstic.dto.load;

/** Evidence supplied with an intentional business-date command, never inferred from an instant. */
public record PickupBusinessDateProvenance(String reasonCode, String reason, String source) { }
