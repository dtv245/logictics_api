package com.company.logicstic.inspection;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;
import java.util.UUID;

/**
 * Public API of the inspection feature — cargo condition reports and their defects ({@code
 * docs/docs/api/overview.md}, Inspections).
 */
public interface InspectionService
    extends CrudService<LoadConditionReport, InspectionResponse, CreateInspectionRequest> {

  /**
   * Searches condition reports with optional load and type filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<InspectionResponse> search(
      UUID loadId, String type, int page, int pageSize, String orderBy, boolean descending);
}
