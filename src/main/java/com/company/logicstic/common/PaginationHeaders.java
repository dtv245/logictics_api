package com.company.logicstic.common;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;

/** Pagination metadata for legacy endpoints whose response body is a bare array. */
public final class PaginationHeaders {
    private PaginationHeaders() {}

    public static HttpHeaders from(Page<?> page) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Pagination-Page", Integer.toString(page.getNumber() + 1));
        headers.add("X-Pagination-Size", Integer.toString(page.getSize()));
        headers.add("X-Pagination-Total", Long.toString(page.getTotalElements()));
        headers.add("X-Pagination-Total-Pages", Integer.toString(page.getTotalPages()));
        headers.add("X-Pagination-Has-Next", Boolean.toString(page.hasNext()));
        return headers;
    }
}
