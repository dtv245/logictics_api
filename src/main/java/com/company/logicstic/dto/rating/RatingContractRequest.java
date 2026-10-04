package com.company.logicstic.dto.rating;

import java.time.LocalDate;
import java.util.UUID;

public record RatingContractRequest(UUID customerId, String currency, LocalDate effectiveFrom,
                                    LocalDate effectiveTo) { }
