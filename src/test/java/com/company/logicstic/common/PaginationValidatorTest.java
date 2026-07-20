package com.company.logicstic.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link PaginationValidator} to ensure page/pageSize bounds
 * are enforced correctly for all edge cases.
 */
@DisplayName("PaginationValidator")
class PaginationValidatorTest {

    // ── validatePage ──────────────────────────────────────────────────

    @Test
    @DisplayName("validatePage: valid positive page is returned as-is")
    void validatePage_positive() {
        assertEquals(3, PaginationValidator.validatePage(3));
    }

    @Test
    @DisplayName("validatePage: page=1 is returned as-is")
    void validatePage_one() {
        assertEquals(1, PaginationValidator.validatePage(1));
    }

    @Test
    @DisplayName("validatePage: page=0 falls back to default")
    void validatePage_zero() {
        assertEquals(Constants.DEFAULT_PAGE, PaginationValidator.validatePage(0));
    }

    @Test
    @DisplayName("validatePage: negative page falls back to default")
    void validatePage_negative() {
        assertEquals(Constants.DEFAULT_PAGE, PaginationValidator.validatePage(-5));
    }

    @Test
    @DisplayName("validatePage: null page falls back to default")
    void validatePage_null() {
        assertEquals(Constants.DEFAULT_PAGE, PaginationValidator.validatePage(null));
    }

    @Test
    @DisplayName("validatePage: large page number is valid")
    void validatePage_large() {
        assertEquals(Integer.MAX_VALUE, PaginationValidator.validatePage(Integer.MAX_VALUE));
    }

    // ── validatePageSize ──────────────────────────────────────────────

    @Test
    @DisplayName("validatePageSize: valid size within bounds is returned as-is")
    void validatePageSize_withinBounds() {
        assertEquals(50, PaginationValidator.validatePageSize(50));
    }

    @Test
    @DisplayName("validatePageSize: size=1 is returned as-is")
    void validatePageSize_one() {
        assertEquals(1, PaginationValidator.validatePageSize(1));
    }

    @Test
    @DisplayName("validatePageSize: size at MAX bound is returned as-is")
    void validatePageSize_atMax() {
        assertEquals(Constants.MAX_PAGE_SIZE, PaginationValidator.validatePageSize(Constants.MAX_PAGE_SIZE));
    }

    @Test
    @DisplayName("validatePageSize: size exceeding MAX is clamped down")
    void validatePageSize_exceedsMax() {
        assertEquals(Constants.MAX_PAGE_SIZE, PaginationValidator.validatePageSize(Constants.MAX_PAGE_SIZE + 1));
    }

    @Test
    @DisplayName("validatePageSize: size=0 falls back to default")
    void validatePageSize_zero() {
        assertEquals(Constants.DEFAULT_PAGE_SIZE, PaginationValidator.validatePageSize(0));
    }

    @Test
    @DisplayName("validatePageSize: negative size falls back to default")
    void validatePageSize_negative() {
        assertEquals(Constants.DEFAULT_PAGE_SIZE, PaginationValidator.validatePageSize(-1));
    }

    @Test
    @DisplayName("validatePageSize: null size falls back to default")
    void validatePageSize_null() {
        assertEquals(Constants.DEFAULT_PAGE_SIZE, PaginationValidator.validatePageSize(null));
    }
}