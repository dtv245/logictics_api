package com.company.logicstic.customer;

import com.company.logicstic.cache.CacheNames;
import com.company.logicstic.shared.persistence.AbstractBaseService;
import com.company.logicstic.shared.web.PagedResponse;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
public class CustomerServiceImpl
    extends AbstractBaseService<Customer, CustomerResponse, CreateCustomerRequest>
    implements CustomerService {

  private final CustomerRepository customerRepository;

  public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
    super(
        customerRepository,
        customerMapper::toResponse,
        customerMapper::toEntity,
        customerMapper::updateEntity);
    this.customerRepository = customerRepository;
  }

  @Override
  protected String entityName() {
    return "Customer";
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = CacheNames.CUSTOMER, key = "#id")
  public CustomerResponse getById(UUID id) {
    return super.getById(id);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.CUSTOMER, allEntries = true)
  public CustomerResponse create(CreateCustomerRequest request) {
    return super.create(request);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.CUSTOMER, allEntries = true)
  public CustomerResponse update(UUID id, CreateCustomerRequest request) {
    return super.update(id, request);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.CUSTOMER, allEntries = true)
  public void delete(UUID id) {
    super.delete(id);
  }

  public PagedResponse<CustomerResponse> search(
      String search, String status, int page, int pageSize, String orderBy, boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(customerRepository.search(search, status, pageable));
  }
}
