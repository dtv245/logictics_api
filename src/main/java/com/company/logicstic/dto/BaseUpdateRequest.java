package com.company.logicstic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for all Update/PUT request DTOs.
 * Inheriting classes should add entity-specific fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseUpdateRequest {
    // Subclasses define their own fields
}
