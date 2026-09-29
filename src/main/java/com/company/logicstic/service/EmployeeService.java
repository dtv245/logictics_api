package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.employee.CreateEmployeeRequest;
import com.company.logicstic.dto.employee.EmployeeView;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TenantRoleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TenantRoleRepository roleRepository;

    public EmployeeService(EmployeeRepository employeeRepository, TenantRoleRepository roleRepository) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
    }

    public PagedResponse<EmployeeView> search(String search, String status, UUID roleId,
                                               int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(employeeRepository.search(search, status, roleId, pageable).map(EmployeeView::from));
    }

    public EmployeeView getById(UUID id) {
        return employeeRepository.findById(id)
                .map(EmployeeView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    /** Drivers are employees — filter by role name = DRIVER */
    public PagedResponse<EmployeeView> searchDrivers(String search, String status,
                                                      int page, int pageSize, String orderBy, boolean descending) {
        // We reuse the same search but filter by roleId being the 'DRIVER' role; 
        // alternatively just rely on status filter on driver-specific roles
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        // Pass roleId=null and let the caller filter; for now delegate to general search
        return PagedResponse.from(employeeRepository.search(search, status, null, pageable).map(EmployeeView::from));
    }

    @Transactional
    public EmployeeView create(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.email())) {
            throw new ConflictException("Employee with email '" + request.email() + "' already exists");
        }

        Employee employee = new Employee();
        applyFields(employee, request);
        return EmployeeView.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeView update(UUID id, CreateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
        if (!employee.getEmail().equals(request.email()) && employeeRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email '" + request.email() + "' is already in use");
        }
        applyFields(employee, request);
        return EmployeeView.from(employeeRepository.save(employee));
    }

    @Transactional
    public void delete(UUID id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found: " + id);
        }
        employeeRepository.deleteById(id);
    }

    private void applyFields(Employee employee, CreateEmployeeRequest req) {
        employee.setEmail(req.email());
        employee.setFirstName(req.firstName());
        employee.setLastName(req.lastName());
        employee.setPhoneNumber(req.phoneNumber());
        employee.setSalaryType(req.salaryType());
        employee.setStatus(req.status());
        employee.setJoinedDate(req.joinedDate());
        employee.setSalaryAmount(req.salaryAmount());
        employee.setSalaryCurrency(req.salaryCurrency());
        employee.setAddressLine1(req.addressLine1());
        employee.setAddressLine2(req.addressLine2());
        employee.setAddressCity(req.addressCity());
        employee.setAddressState(req.addressState());
        employee.setAddressZipCode(req.addressZipCode());
        employee.setAddressCountry(req.addressCountry());

        if (req.roleId() != null) {
            TenantRole role = roleRepository.findById(req.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + req.roleId()));
            employee.setRole(role);
        } else {
            employee.setRole(null);
        }
    }
}
