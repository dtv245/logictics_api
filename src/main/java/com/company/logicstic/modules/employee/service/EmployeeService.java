package com.company.logicstic.modules.employee.service;

import com.company.logicstic.modules.employee.dto.request.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.response.EmployeeResponse;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;
import java.util.UUID;

/**
 * Public API of the employee feature, covering both office staff and drivers.
 *
 * <p>A driver is an {@link Employee} rather than a separate entity, so the driver-scoped reads live
 * here too and simply apply the driver filter — {@code /api/drivers} is a projection of the same
 * aggregate, not a second one.
 */
public interface EmployeeService
    extends CrudService<Employee, EmployeeResponse, CreateEmployeeRequest> {

  /**
   * Searches employees with optional free-text, status and role filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<EmployeeResponse> search(
      String search,
      String status,
      UUID roleId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);

  /** Searches only employees who are drivers. */
  PagedResponse<EmployeeResponse> searchDrivers(
      String search, String status, int page, int pageSize, String orderBy, boolean descending);

  /**
   * Reads a single driver.
   *
   * @throws com.company.logicstic.shared.exception.ResourceNotFoundException when the id is unknown
   *     or the employee is not a driver
   */
  EmployeeResponse getDriverById(UUID id);
}
