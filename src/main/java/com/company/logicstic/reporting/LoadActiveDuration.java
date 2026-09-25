package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoadActiveDuration(
    UUID truckId, OffsetDateTime dispatchedAt, OffsetDateTime deliveredAt) {}
