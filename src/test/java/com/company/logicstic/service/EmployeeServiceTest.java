package com.company.logicstic.service;

import com.company.logicstic.modules.employee.dto.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.EmployeeView;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.modules.employee.mapper.EmployeeMapper;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.repository.TenantRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link EmployeeService}.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TenantRoleRepository roleRepository;

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeService employeeService;

    private CreateEmployeeRequest sampleRequest(UUID roleId) {
        return new CreateEmployeeRequest(
                "emp@example.com", "John", "Doe",
                "+111", "HOURLY", "ACTIVE",
                OffsetDateTime.now(), roleId,
                BigDecimal.valueOf(20), "USD",
                null, null, null, null, null, null
        );
    }

    @Test
    void create_conflictingEmail_throwsConflictException() {
        CreateEmployeeRequest req = sampleRequest(null);
        when(employeeRepository.existsByEmail(req.email())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(req.email());

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_withRole_resolvesRoleAndSaves() {
        UUID roleId = UUID.randomUUID();
        CreateEmployeeRequest req = sampleRequest(roleId);
        TenantRole role = new TenantRole();
        role.setId(roleId);
        Employee entity = new Employee();
        EmployeeView view = mock(EmployeeView.class);

        when(employeeRepository.existsByEmail(req.email())).thenReturn(false);
        when(employeeMapper.toEntity(req)).thenReturn(entity);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(employeeRepository.save(entity)).thenReturn(entity);
        when(employeeMapper.toView(entity)).thenReturn(view);

        employeeService.create(req);

        verify(employeeMapper).toEntity(req);
        verify(roleRepository).findById(roleId);
        assertThat(entity.getRole()).isEqualTo(role); // role set by resolveRole()
        verify(employeeRepository).save(entity);
    }

    @Test
    void create_withoutRole_setsRoleNull() {
        CreateEmployeeRequest req = sampleRequest(null);
        Employee entity = new Employee();
        EmployeeView view = mock(EmployeeView.class);

        when(employeeRepository.existsByEmail(req.email())).thenReturn(false);
        when(employeeMapper.toEntity(req)).thenReturn(entity);
        when(employeeRepository.save(entity)).thenReturn(entity);
        when(employeeMapper.toView(entity)).thenReturn(view);

        employeeService.create(req);

        assertThat(entity.getRole()).isNull();
        verify(roleRepository, never()).findById(any());
    }

    @Test
    void create_withInvalidRoleId_throwsResourceNotFoundException() {
        UUID roleId = UUID.randomUUID();
        CreateEmployeeRequest req = sampleRequest(roleId);
        Employee entity = new Employee();

        when(employeeRepository.existsByEmail(req.email())).thenReturn(false);
        when(employeeMapper.toEntity(req)).thenReturn(entity);
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(roleId.toString());
    }

    @Test
    void update_emailConflict_throwsConflictException() {
        UUID id = UUID.randomUUID();
        Employee existing = new Employee();
        existing.setId(id);
        existing.setEmail("old@example.com");

        CreateEmployeeRequest req = sampleRequest(null);
        // req.email() != existing.email(), and new email is taken
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.existsByEmail(req.email())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.update(id, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> employeeService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
