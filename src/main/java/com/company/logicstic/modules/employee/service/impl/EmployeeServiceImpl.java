package com.company.logicstic.modules.employee.service.impl;

import com.company.logicstic.modules.employee.dto.request.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.response.EmployeeResponse;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.mapper.EmployeeMapper;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.modules.role.service.RoleService;
import com.company.logicstic.shared.common.CacheNames;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class EmployeeServiceImpl
    extends AbstractBaseService<Employee, EmployeeResponse, CreateEmployeeRequest>
    implements EmployeeService {

  private final EmployeeRepository employeeRepository;
  private final RoleService roleService;
  private final EmployeeMapper employeeMapper;

  public EmployeeServiceImpl(
      EmployeeRepository employeeRepository,
      RoleService roleService,
      EmployeeMapper employeeMapper) {
    super(
        employeeRepository,
        employeeMapper::toResponse,
        employeeMapper::toEntity,
        employeeMapper::updateEntity);
    this.employeeRepository = employeeRepository;
    this.roleService = roleService;
    this.employeeMapper = employeeMapper;
  }

  @Override
  protected String entityName() {
    return "Employee";
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = CacheNames.EMPLOYEE, key = "#id")
  public EmployeeResponse getById(UUID id) {
    return super.getById(id);
  }

  // Also clears truck: TruckResponse copies mainDriverName/secondaryDriverName
  // from the employee, so a rename here would leave stale names cached over there.
  @Override
  @Transactional
  @CacheEvict(
      cacheNames = {CacheNames.EMPLOYEE, CacheNames.TRUCK},
      allEntries = true)
  public EmployeeResponse create(CreateEmployeeRequest request) {
    return super.create(request);
  }

  @Override
  @Transactional
  @CacheEvict(
      cacheNames = {CacheNames.EMPLOYEE, CacheNames.TRUCK},
      allEntries = true)
  public EmployeeResponse update(UUID id, CreateEmployeeRequest request) {
    return super.update(id, request);
  }

  @Override
  @Transactional
  @CacheEvict(
      cacheNames = {CacheNames.EMPLOYEE, CacheNames.TRUCK},
      allEntries = true)
  public void delete(UUID id) {
    super.delete(id);
  }

  public PagedResponse<EmployeeResponse> search(
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

  public PagedResponse<EmployeeResponse> searchDrivers(
      String search, String status, int page, int pageSize, String orderBy, boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(employeeRepository.searchDrivers(search, status, pageable));
  }

  // Distinct key space from getById: the same id resolves here only when the employee is a
  // driver, and returns 404 otherwise, so the two answers must not share a cache entry.
  @Cacheable(cacheNames = CacheNames.EMPLOYEE, key = "'driver:' + #id")
  public EmployeeResponse getDriverById(UUID id) {
    return employeeRepository
        .findDriverById(id)
        .map(employeeMapper::toResponse)
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
      TenantRole role = roleService.getEntityById(req.roleId());
      employee.setRole(role);
    } else {
      employee.setRole(null);
    }
  }
}
