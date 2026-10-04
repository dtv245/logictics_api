package com.company.logicstic.dto.load;

import java.util.List;
import java.util.UUID;

public record LoadTimelineResponse(
        UUID loadId,
        String loadNumber,
        String currentStatus,
        List<LoadEventView> events
) {}
