package com.company.logicstic.integration.lark.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.company.logicstic.entity.Customer;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LarkBaseServiceTest {

  @Mock private LarkBaseClient baseClient;
  @Mock private LoadRepository loadRepository;
  @Mock private EmployeeRepository employeeRepository;

  private LarkBaseProperties baseProperties;
  private LarkBaseService baseService;

  @BeforeEach
  void setUp() {
    baseProperties = new LarkBaseProperties(true, "bas_app_token_123", "tbl_loads_123", "tbl_drivers_123");
    baseService = new LarkBaseService(baseProperties, baseClient, loadRepository, employeeRepository);
  }

  @Test
  @DisplayName("syncLoad successfully maps load fields and creates Lark Base record")
  void syncLoad_success() {
    UUID loadId = UUID.randomUUID();
    Load load = new Load();
    load.setId(loadId);
    load.setName("Chicago to Dallas Reefer");
    load.setStatus("ASSIGNED");
    load.setRequestedDeliveryDate(OffsetDateTime.now().plusDays(2));

    Customer customer = new Customer();
    customer.setName("Acme Logistics");
    load.setCustomer(customer);

    Truck truck = new Truck();
    truck.setNumber("TRK-8899");
    load.setAssignedTruck(truck);

    Employee dispatcher = new Employee();
    dispatcher.setFirstName("Sarah");
    dispatcher.setLastName("Connor");
    load.setAssignedDispatcher(dispatcher);

    given(loadRepository.findById(loadId)).willReturn(Optional.of(load));

    LarkBaseRecord mockCreated = LarkBaseRecord.of("rec_load_created_123", Map.of("Load Name", "Chicago to Dallas Reefer"));
    given(baseClient.createRecord(eq("bas_app_token_123"), eq("tbl_loads_123"), any(LarkBaseRecord.class)))
        .willReturn(mockCreated);

    LarkBaseRecord result = baseService.syncLoad(loadId);

    assertThat(result).isNotNull();
    assertThat(result.recordId()).isEqualTo("rec_load_created_123");
  }

  @Test
  @DisplayName("syncDriver successfully maps employee fields and creates Lark Base record")
  void syncDriver_success() {
    UUID employeeId = UUID.randomUUID();
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setFirstName("James");
    employee.setLastName("Bond");
    employee.setEmail("james.bond@mi6.gov.uk");
    employee.setPhoneNumber("+44700000007");
    employee.setStatus("ACTIVE");
    employee.setSalaryType("MILEAGE");

    given(employeeRepository.findById(employeeId)).willReturn(Optional.of(employee));

    LarkBaseRecord mockCreated = LarkBaseRecord.of("rec_driver_created_777", Map.of("Full Name", "James Bond"));
    given(baseClient.createRecord(eq("bas_app_token_123"), eq("tbl_drivers_123"), any(LarkBaseRecord.class)))
        .willReturn(mockCreated);

    LarkBaseRecord result = baseService.syncDriver(employeeId);

    assertThat(result).isNotNull();
    assertThat(result.recordId()).isEqualTo("rec_driver_created_777");
  }

  @Test
  @DisplayName("syncLoad throws exception if Base appToken is not configured")
  void syncLoad_unconfiguredBase_throwsException() {
    LarkBaseProperties unconfigured = new LarkBaseProperties(true, "", "tbl_loads", "tbl_drivers");
    LarkBaseService service = new LarkBaseService(unconfigured, baseClient, loadRepository, employeeRepository);

    UUID loadId = UUID.randomUUID();
    assertThatThrownBy(() -> service.syncLoad(loadId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("appToken chưa được cấu hình");
  }
}
