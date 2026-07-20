package com.company.logicstic.service;

import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.dto.customer.CreateCustomerRequest;
import com.company.logicstic.dto.customer.CustomerView;
import com.company.logicstic.entity.Customer;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.CustomerMapper;
import com.company.logicstic.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link CustomerService} — verifies CRUD behavior parity
 * after mapper refactor. Uses Mockito to avoid DB dependency.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerService customerService;

    private Customer sampleCustomer() {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setName("Acme Corp");
        c.setEmail("acme@example.com");
        c.setStatus("ACTIVE");
        return c;
    }

    private CustomerView sampleView(UUID id) {
        return new CustomerView(id, "Acme Corp", "acme@example.com", null,
                "ACTIVE", null, null, false, null, null, null, null, null, null);
    }

    /** CreateCustomerRequest has 13 fields: name, email, phone, status, notes, taxId, isVatExempt, addr... */
    private CreateCustomerRequest sampleRequest() {
        return new CreateCustomerRequest("Corp", "corp@example.com", null, "ACTIVE",
                null, null, false, null, null, null, null, null, null);
    }

    @Test
    void search_delegatesToRepositoryAndMapper() {
        Customer c = sampleCustomer();
        CustomerView view = sampleView(c.getId());
        Page<Customer> page = new PageImpl<>(List.of(c));

        when(customerRepository.search(any(), any(), any(Pageable.class))).thenReturn(page);
        when(customerMapper.toView(c)).thenReturn(view);

        PagedResponse<CustomerView> result = customerService.search(null, null, 1, 20, "name", false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("Acme Corp");
        verify(customerMapper).toView(c);
    }

    @Test
    void getById_found_returnsView() {
        Customer c = sampleCustomer();
        CustomerView view = sampleView(c.getId());

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(customerMapper.toView(c)).thenReturn(view);

        CustomerView result = customerService.getById(c.getId());
        assertThat(result.id()).isEqualTo(c.getId());
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void create_usesMapperToEntityAndToView() {
        CreateCustomerRequest req = sampleRequest();
        Customer entity = new Customer();
        entity.setId(UUID.randomUUID());
        CustomerView view = sampleView(entity.getId());

        when(customerMapper.toEntity(req)).thenReturn(entity);
        when(customerRepository.save(entity)).thenReturn(entity);
        when(customerMapper.toView(entity)).thenReturn(view);

        CustomerView result = customerService.create(req);

        verify(customerMapper).toEntity(req);
        verify(customerRepository).save(entity);
        verify(customerMapper).toView(entity);
        assertThat(result).isEqualTo(view);
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(id, sampleRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_found_callsUpdateEntityAndSave() {
        Customer existing = sampleCustomer();
        UUID id = existing.getId();
        CustomerView view = sampleView(id);

        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(customerRepository.save(existing)).thenReturn(existing);
        when(customerMapper.toView(existing)).thenReturn(view);

        customerService.update(id, sampleRequest());

        verify(customerMapper).updateEntity(sampleRequest(), existing);
        verify(customerRepository).save(existing);
    }

    @Test
    void delete_found_callsDeleteById() {
        UUID id = UUID.randomUUID();
        when(customerRepository.existsById(id)).thenReturn(true);

        customerService.delete(id);

        verify(customerRepository).deleteById(id);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(customerRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> customerService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
