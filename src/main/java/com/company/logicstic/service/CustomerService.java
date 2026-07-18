package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.customer.CreateCustomerRequest;
import com.company.logicstic.dto.customer.CustomerView;
import com.company.logicstic.entity.Customer;
import com.company.logicstic.exception.ResourceNotFoundException;
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

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public PagedResponse<CustomerView> search(String search, String status, int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(customerRepository.search(search, status, pageable).map(CustomerView::from));
    }

    public CustomerView getById(UUID id) {
        return customerRepository.findById(id)
                .map(CustomerView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    @Transactional
    public CustomerView create(CreateCustomerRequest request) {
        Customer customer = new Customer();
        applyFields(customer, request);
        return CustomerView.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerView update(UUID id, CreateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        applyFields(customer, request);
        return CustomerView.from(customerRepository.save(customer));
    }

    @Transactional
    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer not found: " + id);
        }
        customerRepository.deleteById(id);
    }

    private void applyFields(Customer customer, CreateCustomerRequest req) {
        customer.setName(req.name());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setStatus(req.status());
        customer.setNotes(req.notes());
        customer.setTaxId(req.taxId());
        customer.setIsVatExempt(req.isVatExempt() != null ? req.isVatExempt() : false);
        customer.setAddressLine1(req.addressLine1());
        customer.setAddressLine2(req.addressLine2());
        customer.setAddressCity(req.addressCity());
        customer.setAddressState(req.addressState());
        customer.setAddressZipCode(req.addressZipCode());
        customer.setAddressCountry(req.addressCountry());
    }
}
