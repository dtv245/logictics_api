package com.company.logicstic.shared.dto;

import lombok.Data;

/**
 * Base class for all Create/POST request DTOs. Inheriting classes should add entity-specific
 * fields.
 */
@Data
public abstract class BaseCreateRequest {
  // Subclasses define their own fields
}
