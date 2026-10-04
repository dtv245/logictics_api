package com.company.logicstic.dto.load;

import java.time.LocalDate;
import java.util.UUID;

public record SetPickupBusinessDateRequest(LocalDate requestedPickupBusinessDate,
        PickupBusinessDateProvenance provenance, UUID expectedChangeId) { }
