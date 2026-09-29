package com.company.logicstic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination metadata for list responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {

    @JsonProperty("page")
    private int pageNumber;

    @JsonProperty("size")
    private int pageSize;

    @JsonProperty("total")
    private long totalElements;

    @JsonProperty("pages")
    private int totalPages;

    @JsonProperty("hasNext")
    private boolean hasNext;

    @JsonProperty("hasPrevious")
    private boolean hasPrevious;

    /**
     * Create PageInfo from Spring's Pageable result
     */
    public static PageInfo of(org.springframework.data.domain.Page<?> page) {
        return PageInfo.builder()
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
