package com.company.logicstic.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        long totalItems,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean hasNext
) {
    /** Keeps existing construction sites source-compatible. */
    public PagedResponse(List<T> items, long totalItems, int totalPages, int currentPage, int pageSize) {
        this(items, totalItems, totalPages, currentPage, pageSize, false);
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber() + 1,  // 1-based
                page.getSize(),
                page.hasNext()
        );
    }
}
