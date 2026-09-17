package com.company.logicstic.customer;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;

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
