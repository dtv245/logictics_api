package com.company.logicstic.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for all Update/PUT request DTOs.
 * <p>
 * Inheriting classes add entity-specific fields.
 * Uses {@code @Data} for getters/setters and {@code @NoArgsConstructor}
 * for JPA/Jackson deserialization.
 */
@Data
@NoArgsConstructor
public abstract class BaseUpdateRequest {
    // Subclasses define their own fields
}
