package com.company.logicstic.dto.load;

import com.company.logicstic.dto.PageInfo;
import java.util.List;
import java.util.UUID;

public record LoadTimelineResponse(
        UUID loadId,
        String loadNumber,
        String currentStatus,
        List<LoadEventView> events,
        PageInfo pagination
) {
    public LoadTimelineResponse(UUID loadId, String loadNumber, String currentStatus, List<LoadEventView> events) {
        this(loadId, loadNumber, currentStatus, events, null);
    }
}
