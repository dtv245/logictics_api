package com.company.logicstic.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Exception for resource not found scenarios. */
@Getter
public class ResourceNotFoundException extends ApiException {

  private final String resourceName;
  private final String fieldName;
  private final Object fieldValue;

  public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
    super(
        HttpStatus.NOT_FOUND,
        "RESOURCE_NOT_FOUND",
        String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue));
    this.resourceName = resourceName;
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public ResourceNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    this.resourceName = null;
    this.fieldName = null;
    this.fieldValue = null;
  }
}
