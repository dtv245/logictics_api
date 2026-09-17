package com.company.logicstic.fleet.truck;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;

/**
 * Public API of the fleet feature.
 *
 * <p>A truck carries its main and secondary driver as associations resolved through {@code
 * EmployeeService}; load and trip obtain a {@link Truck} through {@link #getEntityById}.
 */
public interface TruckService extends CrudService<Truck, TruckResponse, CreateTruckRequest> {

  /**
   * Searches trucks with optional free-text, status and type filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<TruckResponse> search(
      String search,
      String status,
      String type,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);
}
