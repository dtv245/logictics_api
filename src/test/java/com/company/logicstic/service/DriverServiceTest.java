package com.company.logicstic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.employee.mapper.EmployeeMapper;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.role.repository.TenantRoleRepository;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

class DriverServiceTest {

  @Test
  void driverListUsesRoleClaimFilteredRepositoryQuery() {
    AtomicReference<String> called = new AtomicReference<>();
    EmployeeRepository repository =
        proxy(
            EmployeeRepository.class,
            (method, args) -> {
              called.set(method);
              if (method.equals("searchDrivers")) {
                return Page.empty();
              }
              throw new AssertionError("Unexpected EmployeeRepository call: " + method);
            });
    EmployeeService service = service(repository);

    assertThat(service.searchDrivers(null, null, 1, 20, "lastName", false).items()).isEmpty();
    assertThat(called.get()).isEqualTo("searchDrivers");
  }

  @Test
  void driverDetailRejectsEmployeeWithoutDriverClaim() {
    UUID id = UUID.randomUUID();
    EmployeeRepository repository =
        proxy(
            EmployeeRepository.class,
            (method, args) -> {
              if (method.equals("findDriverById")) {
                return Optional.empty();
              }
              throw new AssertionError("Unexpected EmployeeRepository call: " + method);
            });
    EmployeeService service = service(repository);

    assertThatThrownBy(() -> service.getDriverById(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Driver not found");
  }

  private EmployeeService service(EmployeeRepository repository) {
    TenantRoleRepository roleRepository =
        proxy(
            TenantRoleRepository.class,
            (method, args) -> {
              throw new AssertionError("Unexpected TenantRoleRepository call: " + method);
            });
    EmployeeMapper mapper = Mappers.getMapper(EmployeeMapper.class);
    return new EmployeeService(repository, roleRepository, mapper);
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
