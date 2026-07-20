package com.company.logicstic.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for all View/Response DTOs. Includes audit fields: id, createdBy, createdAt,
 * lastModifiedBy, lastModifiedAt Inheriting classes should add entity-specific fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseView {

  protected UUID id;

  protected String createdBy;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  protected LocalDateTime createdAt;

  protected String lastModifiedBy;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  protected LocalDateTime lastModifiedAt;
}
