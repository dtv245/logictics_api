package com.company.logicstic.modules.inspection.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DefectRequest(
    @NotBlank String partCategory, @NotBlank String description, @NotBlank String severity) {}
