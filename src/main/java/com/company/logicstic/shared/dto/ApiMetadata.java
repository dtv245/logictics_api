package com.company.logicstic.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Metadata for API responses, including pagination and request info. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiMetadata {

  /** Response timestamp */
  private Instant timestamp;

  /** Request path */
  private String path;

  /** Unique request ID for tracing */
  private String requestId;

  /** Pagination info (for list responses) */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private PageInfo pagination;

  /** Create metadata with pagination from a Spring Page */
  public static ApiMetadata withPagination(
      org.springframework.data.domain.Page<?> page,
      jakarta.servlet.http.HttpServletRequest request) {
    return ApiMetadata.builder()
        .timestamp(Instant.now())
        .path(request.getRequestURI())
        .requestId(request.getHeader("X-Request-Id"))
        .pagination(PageInfo.of(page))
        .build();
  }

  /** Create metadata without pagination */
  public static ApiMetadata of(jakarta.servlet.http.HttpServletRequest request) {
    return ApiMetadata.builder()
        .timestamp(Instant.now())
        .path(request.getRequestURI())
        .requestId(request.getHeader("X-Request-Id"))
        .build();
  }
}
