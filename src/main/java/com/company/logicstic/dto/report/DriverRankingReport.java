package com.company.logicstic.dto.report;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DriverRankingReport(
        ReportPeriodDto period,
        String currency,
        String sortBy,
        List<DriverRankingItem> drivers,
        DriverRankingWeights weights,
        @JsonProperty("isMock") boolean isMock
) {}
