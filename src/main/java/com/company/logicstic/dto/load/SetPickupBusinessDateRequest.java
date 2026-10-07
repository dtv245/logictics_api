package com.company.logicstic.dto.load;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SetPickupBusinessDateRequest(
        @NotNull LocalDate requestedPickupBusinessDate,
        @NotNull @jakarta.validation.Valid PickupBusinessDateProvenance provenance,
        UUID expectedChangeId
) { }
