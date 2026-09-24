package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DowntimeInterval(
    UUID truckId, OffsetDateTime downtimeStartAt, OffsetDateTime downtimeEndAt) {}
