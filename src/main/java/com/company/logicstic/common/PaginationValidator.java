package com.company.logicstic.common;

import org.springframework.validation.annotation.Validated;

/**
 * Validates pagination parameters for search endpoints.
 * <p>
 * Enforces minimum/maximum bounds on page and pageSize to prevent
 * abuse or excessive database load.
 */
@Validated
public final class PaginationValidator {

    public static final int MAX_LOOKUP_PAGE_SIZE = 50;

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
        if (pageSize == null) {
            return Constants.DEFAULT_PAGE_SIZE;
        }
        return Math.clamp(pageSize, 1, Constants.MAX_PAGE_SIZE);
    }

    /**
     * Normalizes the 0-based page convention used by the AI and payroll APIs.
     */
    public static int validateZeroBasedPage(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    /**
     * Appends a stable id tie-breaker so rows with equal primary sort values
     * cannot move between adjacent pages while the underlying data is stable.
     */
    public static org.springframework.data.domain.Sort stableSort(org.springframework.data.domain.Sort sort) {
        var effectiveSort = sort == null ? org.springframework.data.domain.Sort.unsorted() : sort;
        return effectiveSort.getOrderFor("id") == null
                ? effectiveSort.and(org.springframework.data.domain.Sort.by("id"))
                : effectiveSort;
    }

    public static org.springframework.data.domain.PageRequest pageRequest(int page, int size, org.springframework.data.domain.Sort sort) {
        return org.springframework.data.domain.PageRequest.of(
                validatePage(page) - 1,
                validatePageSize(size),
                stableSort(sort)
        );
    }

    public static org.springframework.data.domain.PageRequest zeroBasedPageRequest(
            Integer page,
            Integer size,
            org.springframework.data.domain.Sort sort
    ) {
        return org.springframework.data.domain.PageRequest.of(
                validateZeroBasedPage(page),
                validatePageSize(size),
                stableSort(sort)
        );
    }

    public static int validateLookupPageSize(Integer pageSize) {
        if (pageSize == null) {
            return Constants.DEFAULT_PAGE_SIZE;
        }
        return Math.clamp(pageSize, 1, MAX_LOOKUP_PAGE_SIZE);
    }

    public static org.springframework.data.domain.PageRequest lookupPageRequest(
            Integer page,
            Integer pageSize,
            org.springframework.data.domain.Sort sort
    ) {
        return org.springframework.data.domain.PageRequest.of(
                validatePage(page) - 1,
                validateLookupPageSize(pageSize),
                stableSort(sort)
        );
    }
}
