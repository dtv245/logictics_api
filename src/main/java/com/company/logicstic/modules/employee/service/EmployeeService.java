package com.company.logicstic.modules.employee.service;

import com.company.logicstic.modules.employee.dto.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.EmployeeView;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.mapper.EmployeeMapper;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.modules.role.repository.TenantRoleRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class EmployeeService
    extends AbstractBaseService<Employee, EmployeeView, CreateEmployeeRequest> {

  private final EmployeeRepository employeeRepository;
  private final TenantRoleRepository roleRepository;
  private final EmployeeMapper employeeMapper;

  public EmployeeService(
      EmployeeRepository employeeRepository,
      TenantRoleRepository roleRepository,
      EmployeeMapper employeeMapper) {
    super(
        employeeRepository,
        employeeMapper::toView,
        employeeMapper::toEntity,
        employeeMapper::updateEntity);
    this.employeeRepository = employeeRepository;
    this.roleRepository = roleRepository;
    this.employeeMapper = employeeMapper;
  }

  @Override
  protected String entityName() {
    return "Employee";
  }

  public PagedResponse<EmployeeView> search(
      String search,
      String status,
      UUID roleId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(employeeRepository.search(search, status, roleId, pageable));
  }

  public PagedResponse<EmployeeView> searchDrivers(
      String search, String status, int page, int pageSize, String orderBy, boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(employeeRepository.searchDrivers(search, status, pageable));
  }

  public EmployeeView getDriverById(UUID id) {
    return employeeRepository
        .findDriverById(id)
        .map(employeeMapper::toView)
        .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
  }

  @Override
  protected void beforeCreate(Employee employee, CreateEmployeeRequest request) {
    if (employeeRepository.existsByEmail(request.email())) {
      throw new ConflictException("Employee with email '" + request.email() + "' already exists");
    }
    resolveRole(employee, request);
  }

  @Override
  protected void beforeMapUpdate(Employee employee, CreateEmployeeRequest request) {
    if (!Objects.equals(employee.getEmail(), request.email())
        && employeeRepository.existsByEmail(request.email())) {
      throw new ConflictException("Email '" + request.email() + "' is already in use");
    }
  }

  @Override
  protected void beforeUpdate(Employee employee, CreateEmployeeRequest request) {
    resolveRole(employee, request);
  }

  private void resolveRole(Employee employee, CreateEmployeeRequest req) {
    if (req.roleId() != null) {
      TenantRole role =
          roleRepository
              .findById(req.roleId())
              .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + req.roleId()));
      employee.setRole(role);
    } else {
      employee.setRole(null);
    }
  }
}
