package com.company.logicstic.inspection;

import jakarta.validation.constraints.NotBlank;

public record DefectRequest(
    @NotBlank String partCategory, @NotBlank String description, @NotBlank String severity) {}
