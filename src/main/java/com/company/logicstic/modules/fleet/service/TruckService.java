package com.company.logicstic.modules.fleet.service;

import com.company.logicstic.modules.fleet.dto.request.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.response.TruckResponse;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;

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
