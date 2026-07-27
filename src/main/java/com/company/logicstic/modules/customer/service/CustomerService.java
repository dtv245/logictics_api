package com.company.logicstic.modules.customer.service;

import com.company.logicstic.modules.customer.dto.request.CreateCustomerRequest;
import com.company.logicstic.modules.customer.dto.response.CustomerResponse;
import com.company.logicstic.modules.customer.entity.Customer;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;

/**
 * Public API of the customer feature — the shipper whose loads the tenant hauls.
 *
 * <p>Other features (load, invoice) reach a {@link Customer} through {@link #getEntityById} only;
 * they must not inject {@code CustomerRepository} (docs/docs/development/engineering-conventions.md
 * §2).
 */
public interface CustomerService
    extends CrudService<Customer, CustomerResponse, CreateCustomerRequest> {

  /**
   * Searches customers with optional free-text and status filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<CustomerResponse> search(
      String search, String status, int page, int pageSize, String orderBy, boolean descending);
}
