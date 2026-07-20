package com.company.logicstic.modules.customer.service;

import com.company.logicstic.modules.customer.dto.CreateCustomerRequest;
import com.company.logicstic.modules.customer.dto.CustomerView;
import com.company.logicstic.modules.customer.entity.Customer;
import com.company.logicstic.modules.customer.mapper.CustomerMapper;
import com.company.logicstic.modules.customer.repository.CustomerRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("!nodb")
@Service
public class CustomerService
    extends AbstractBaseService<Customer, CustomerView, CreateCustomerRequest> {

  private final CustomerRepository customerRepository;
  private final CustomerMapper customerMapper;

  public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper) {
    super(
        customerRepository,
        customerMapper::toView,
        customerMapper::toEntity,
        customerMapper::updateEntity);
    this.customerRepository = customerRepository;
    this.customerMapper = customerMapper;
  }

  @Override
  protected String entityName() {
    return "Customer";
  }

  public PagedResponse<CustomerView> search(
      String search, String status, int page, int pageSize, String orderBy, boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(customerRepository.search(search, status, pageable));
  }
}
