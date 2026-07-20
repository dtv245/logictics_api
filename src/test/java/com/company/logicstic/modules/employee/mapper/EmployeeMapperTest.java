package com.company.logicstic.modules.employee.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.employee.dto.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.EmployeeView;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.role.entity.TenantRole;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Pure unit tests for {@link EmployeeMapper} using Mappers.getMapper(). No Spring context required
 * — avoids DB dependency in test suite. Verifies parity with the old {@code EmployeeView.from()}
 * logic.
 */
class EmployeeMapperTest {

  private EmployeeMapper employeeMapper;

  @BeforeEach
  void setUp() {
    employeeMapper = Mappers.getMapper(EmployeeMapper.class);
  }

  @Test
  void toView_withRole_flattensRoleFields() {
    TenantRole role = new TenantRole();
    role.setId(UUID.randomUUID());
    role.setName("DRIVER");

    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmail("john@example.com");
    employee.setFirstName("John");
    employee.setLastName("Doe");
    employee.setPhoneNumber("+1234567890");
    employee.setSalaryType("HOURLY");
    employee.setStatus("ACTIVE");
    employee.setJoinedDate(OffsetDateTime.now());
    employee.setSalaryAmount(BigDecimal.valueOf(25.00));
    employee.setSalaryCurrency("USD");
    employee.setRole(role);

    EmployeeView view = employeeMapper.toView(employee);

    assertThat(view.id()).isEqualTo(employee.getId());
    assertThat(view.email()).isEqualTo("john@example.com");
    assertThat(view.firstName()).isEqualTo("John");
    assertThat(view.lastName()).isEqualTo("Doe");
    assertThat(view.roleId()).isEqualTo(role.getId());
    assertThat(view.roleName()).isEqualTo("DRIVER");
    assertThat(view.salaryAmount()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
  }

  @Test
  void toView_withoutRole_producesNullRoleFields() {
    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmail("jane@example.com");
    employee.setFirstName("Jane");
    employee.setLastName("Smith");
    employee.setSalaryType("MONTHLY");
    employee.setStatus("ACTIVE");
    employee.setJoinedDate(OffsetDateTime.now());
    employee.setSalaryAmount(BigDecimal.valueOf(4000));
    employee.setSalaryCurrency("USD");
    employee.setRole(null);

    EmployeeView view = employeeMapper.toView(employee);

    assertThat(view.roleId()).isNull();
    assertThat(view.roleName()).isNull();
  }

  @Test
  void toEntity_mapsScalarFieldsCorrectly_andIgnoresFk() {
    CreateEmployeeRequest req =
        new CreateEmployeeRequest(
            "emp@example.com",
            "Alice",
            "Walker",
            "+555",
            "MONTHLY",
            "ACTIVE",
            OffsetDateTime.now(),
            UUID.randomUUID(),
            BigDecimal.valueOf(5000),
            "EUR",
            "123 Main St",
            null,
            "Berlin",
            "BE",
            "10115",
            "DE");

    Employee entity = employeeMapper.toEntity(req);

    assertThat(entity.getEmail()).isEqualTo("emp@example.com");
    assertThat(entity.getFirstName()).isEqualTo("Alice");
    assertThat(entity.getLastName()).isEqualTo("Walker");
    assertThat(entity.getRole()).isNull(); // FK ignored by mapper
    assertThat(entity.getId()).isNull(); // id not set
  }

  @Test
  void toView_matchesOldFromMethodOutput() {
    TenantRole role = new TenantRole();
    UUID roleId = UUID.randomUUID();
    role.setId(roleId);
    role.setName("ADMIN");

    Employee e = new Employee();
    UUID empId = UUID.randomUUID();
    e.setId(empId);
    e.setEmail("test@co.com");
    e.setFirstName("Test");
    e.setLastName("User");
    e.setPhoneNumber("000");
    e.setSalaryType("FIXED");
    e.setStatus("INACTIVE");
    e.setJoinedDate(OffsetDateTime.parse("2023-01-01T00:00:00Z"));
    e.setSalaryAmount(BigDecimal.valueOf(3000));
    e.setSalaryCurrency("USD");
    e.setAddressLine1("Addr1");
    e.setAddressLine2("Addr2");
    e.setAddressCity("City");
    e.setAddressState("ST");
    e.setAddressZipCode("12345");
    e.setAddressCountry("US");
    e.setRole(role);

    // Old from() logic result
    EmployeeView expected =
        new EmployeeView(
            empId,
            "test@co.com",
            "Test",
            "User",
            "000",
            "FIXED",
            "INACTIVE",
            OffsetDateTime.parse("2023-01-01T00:00:00Z"),
            roleId,
            "ADMIN",
            BigDecimal.valueOf(3000),
            "USD",
            "Addr1",
            "Addr2",
            "City",
            "ST",
            "12345",
            "US");

    EmployeeView actual = employeeMapper.toView(e);
    assertThat(actual).isEqualTo(expected);
  }
}
