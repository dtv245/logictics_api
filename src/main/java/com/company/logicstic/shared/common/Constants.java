package com.company.logicstic.shared.common;

/**
 * Application-wide constants for pagination, validation, and API defaults.
 *
 * <p>Centralizing these values eliminates magic numbers scattered across controllers and ensures
 * consistent behavior across all endpoints.
 */
public final class Constants {

  private Constants() {
    // prevent instantiation
  }

  /** Default page number (1-based) for paginated endpoints. */
  public static final int DEFAULT_PAGE = 1;

  /** Default number of items per page. */
  public static final int DEFAULT_PAGE_SIZE = 20;

  /** Maximum allowed page size to prevent abuse. */
  public static final int MAX_PAGE_SIZE = 100;

  /** Default sort direction — newest first. */
  public static final String DEFAULT_SORT_DIRECTION = "true";

  /** Default sort field for entities with a "name" property. */
  public static final String DEFAULT_SORT_FIELD_NAME = "name";

  /** Default sort field for entities with a "number" property (e.g., invoices). */
  public static final String DEFAULT_SORT_FIELD_NUMBER = "number";

  /** Default sort field for time-based entities. */
  public static final String DEFAULT_SORT_FIELD_CREATED_AT = "createdAt";
}
