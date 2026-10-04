package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.customer.CreateCustomerRequest;
import com.company.logicstic.dto.customer.CustomerView;
import com.company.logicstic.entity.Customer;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.CustomerMapper;
import com.company.logicstic.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    public PagedResponse<CustomerView> search(String search, String status, int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(customerRepository.search(search, status, pageable).map(customerMapper::toView));
    }

    public CustomerView getById(UUID id) {
        return customerRepository.findById(id)
                .map(customerMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    @Transactional
    public CustomerView create(CreateCustomerRequest request) {
        Customer customer = customerMapper.toEntity(request);
        return customerMapper.toView(customerRepository.save(customer));
    }

    @Transactional
    public CustomerView update(UUID id, CreateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        customerMapper.updateEntity(request, customer);
        return customerMapper.toView(customerRepository.save(customer));
    }

    @Transactional
    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer not found: " + id);
        }
        customerRepository.deleteById(id);
    }
}
