package com.company.logicstic.modules.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.employee.service.impl.CurrentEmployeeLookupServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentEmployeeLookupServiceTest {

  @Mock private EmployeeRepository employeeRepository;

  private CurrentEmployeeLookupService service;

  @BeforeEach
  void setUp() {
    service = new CurrentEmployeeLookupServiceImpl(employeeRepository);
  }

  @Test
  void returnsEmployeeIdForCanonicalEmail() {
    UUID employeeId = UUID.randomUUID();
    Employee employee = new Employee();
    employee.setId(employeeId);
    given(employeeRepository.findByEmail("dispatcher@example.com"))
        .willReturn(Optional.of(employee));

    assertThat(service.findEmployeeIdByEmail(" dispatcher@example.com ")).contains(employeeId);
    verify(employeeRepository).findByEmail("dispatcher@example.com");
  }

  @Test
  void returnsEmptyWhenEmployeeDoesNotExist() {
    given(employeeRepository.findByEmail("owner@example.com")).willReturn(Optional.empty());

    assertThat(service.findEmployeeIdByEmail("owner@example.com")).isEmpty();
  }

  @Test
  void doesNotQueryForBlankEmail() {
    assertThat(service.findEmployeeIdByEmail("  ")).isEmpty();
    assertThat(service.findEmployeeIdByEmail(null)).isEmpty();
    verifyNoInteractions(employeeRepository);
  }
}
