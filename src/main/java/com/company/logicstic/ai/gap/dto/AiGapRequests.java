package com.company.logicstic.ai.gap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public class AiGapRequests {

    public record CheckRequest(
        List<UUID> loadIds
    ) {}

    public record CheckResult(
        int analyzedLoadCount,
        int totalGapsFound,
        int newGapsCount,
        int autoResolvedCount
    ) {}

    public record DismissRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {}

    public record AnswerRequest(
        String value,
        boolean confirmProposed,
        String notes
    ) {}
}
