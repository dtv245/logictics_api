package com.company.logicstic.shared.common;

import org.springframework.validation.annotation.Validated;

/**
 * Validates pagination parameters for search endpoints.
 *
 * <p>Enforces minimum/maximum bounds on page and pageSize to prevent abuse or excessive database
 * load.
 */
@Validated
public final class PaginationValidator {

  private PaginationValidator() {
    // prevent instantiation
  }

  /**
   * Validates and normalizes the page number.
   *
   * @param page 1-based page number from request
   * @return validated page number (minimum 1)
   */
  public static int validatePage(Integer page) {
    if (page == null || page < 1) {
      return Constants.DEFAULT_PAGE;
    }
    return page;
  }

  /**
   * Validates and normalizes the page size.
   *
   * @param pageSize requested items per page
   * @return validated page size (bounded by min 1 and max {@link Constants#MAX_PAGE_SIZE})
   */
  public static int validatePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return Constants.DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, Constants.MAX_PAGE_SIZE);
  }
}
