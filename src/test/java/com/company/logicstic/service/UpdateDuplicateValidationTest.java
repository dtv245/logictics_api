package com.company.logicstic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.employee.dto.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.mapper.EmployeeMapper;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.fleet.dto.CreateTruckRequest;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.fleet.mapper.TruckMapper;
import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.modules.role.repository.TenantRoleRepository;
import com.company.logicstic.shared.exception.ConflictException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UpdateDuplicateValidationTest {

  @Test
  void employeeUpdateChecksDuplicateBeforeRealMapperMutatesEmail() {
    UUID id = UUID.randomUUID();
    Employee existing = new Employee();
    existing.setId(id);
    existing.setEmail("old@example.com");
    CreateEmployeeRequest request = employeeRequest("taken@example.com");

    EmployeeRepository employeeRepository =
        proxy(
            EmployeeRepository.class,
            (method, args) -> switch (method) {
              case "findById" -> Optional.of(existing);
              case "existsByEmail" -> true;
              default -> throw new AssertionError("Unexpected EmployeeRepository call: " + method);
            });
    TenantRoleRepository roleRepository =
        proxy(
            TenantRoleRepository.class,
            (method, args) -> {
              throw new AssertionError("Role lookup must not run after duplicate detection");
            });
    EmployeeMapper mapper = Mappers.getMapper(EmployeeMapper.class);
    EmployeeService service = new EmployeeService(employeeRepository, roleRepository, mapper);

    assertThatThrownBy(() -> service.update(id, request)).isInstanceOf(ConflictException.class);
    assertThat(existing.getEmail()).isEqualTo("old@example.com");
  }

  @Test
  void truckUpdateChecksDuplicateBeforeRealMapperMutatesNumber() {
    UUID id = UUID.randomUUID();
    Truck existing = new Truck();
    existing.setId(id);
    existing.setNumber("OLD-001");
    CreateTruckRequest request = truckRequest("TAKEN-002");

    TruckRepository truckRepository =
        proxy(
            TruckRepository.class,
            (method, args) -> switch (method) {
              case "findById" -> Optional.of(existing);
              case "existsByNumber" -> true;
              default -> throw new AssertionError("Unexpected TruckRepository call: " + method);
            });
    EmployeeRepository employeeRepository =
        proxy(
            EmployeeRepository.class,
            (method, args) -> {
              throw new AssertionError("Driver lookup must not run after duplicate detection");
            });
    TruckMapper mapper = Mappers.getMapper(TruckMapper.class);
    TruckService service = new TruckService(truckRepository, employeeRepository, mapper);

    assertThatThrownBy(() -> service.update(id, request)).isInstanceOf(ConflictException.class);
    assertThat(existing.getNumber()).isEqualTo("OLD-001");
  }

  private CreateEmployeeRequest employeeRequest(String email) {
    return new CreateEmployeeRequest(
        email,
        "John",
        "Doe",
        null,
        "HOURLY",
        "ACTIVE",
        OffsetDateTime.now(),
        null,
        new BigDecimal("20.00"),
        "USD",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private CreateTruckRequest truckRequest(String number) {
    return new CreateTruckRequest(
        number,
        "TRACTOR",
        1,
        "ACTIVE",
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        false,
        "",
        null);
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, RepositoryCall call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> call.invoke(method.getName(), args));
  }

  @FunctionalInterface
  private interface RepositoryCall {
    Object invoke(String method, Object[] args);
  }
}
