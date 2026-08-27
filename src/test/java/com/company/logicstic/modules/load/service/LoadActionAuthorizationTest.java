package com.company.logicstic.modules.load.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.logicstic.modules.customer.service.CustomerService;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.fleet.service.ContainerService;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.entity.LoadStatus;
import com.company.logicstic.modules.load.mapper.LoadMapper;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.load.service.impl.LoadServiceImpl;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.modules.role.entity.TenantRoleClaim;
import com.company.logicstic.modules.terminal.service.TerminalService;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class LoadActionAuthorizationTest {

  private LoadRepository loadRepository;
  private EmployeeService employeeService;
  private LoadMapper loadMapper;
  private LoadServiceImpl loadService;

  @BeforeEach
  void setUp() {
    loadRepository = mock(LoadRepository.class);
    employeeService = mock(EmployeeService.class);
    loadMapper = mock(LoadMapper.class);
    ApplicationEventPublisher eventPublisher = event -> {};
    loadService =
        new LoadServiceImpl(
            loadRepository,
            mock(CustomerService.class),
            mock(TruckService.class),
            mock(ContainerService.class),
            mock(TerminalService.class),
            employeeService,
            eventPublisher,
            loadMapper);
    when(loadMapper.toResponse(any(Load.class))).thenReturn(mock(LoadResponse.class));
  }

  @Test
  void assignedMainDriverCanPickUp() {
    Employee driver = employee();
    Load load = loadWithTruck(driver, null, LoadStatus.DISPATCHED);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    when(loadRepository.save(load)).thenReturn(load);
    when(employeeService.getEntityById(driver.getId())).thenReturn(driver);

    loadService.pickUp(load.getId(), driver.getId());

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.PICKED_UP);
    verify(loadRepository).save(load);
  }

  @Test
  void assignedSecondaryDriverCanDeliver() {
    Employee main = employee();
    Employee secondary = employee();
    Load load = loadWithTruck(main, secondary, LoadStatus.PICKED_UP);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    when(loadRepository.save(load)).thenReturn(load);
    when(employeeService.getEntityById(secondary.getId())).thenReturn(secondary);

    loadService.deliver(load.getId(), secondary.getId());

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.DELIVERED);
    verify(loadRepository).save(load);
  }

  @Test
  void unrelatedDriverIsDeniedBeforeSave() {
    Employee assigned = employee();
    Employee outsider = employee();
    Load load = loadWithTruck(assigned, null, LoadStatus.DISPATCHED);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    when(employeeService.getEntityById(outsider.getId())).thenReturn(outsider);

    assertThatThrownBy(() -> loadService.pickUp(load.getId(), outsider.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not authorized");

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.DISPATCHED);
    verify(loadRepository, never()).save(any(Load.class));
  }

  @Test
  void nullActorIsDeniedBeforeSave() {
    Employee assigned = employee();
    Load load = loadWithTruck(assigned, null, LoadStatus.DISPATCHED);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));

    assertThatThrownBy(() -> loadService.pickUp(load.getId(), null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("tenant Employee");

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.DISPATCHED);
    verify(loadRepository, never()).save(any(Load.class));
  }

  @Test
  void loadWithoutAssignedDriverIsDeniedBeforeSave() {
    Load load = loadWithTruck(null, null, LoadStatus.DISPATCHED);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    UUID actorId = UUID.randomUUID();

    assertThatThrownBy(() -> loadService.pickUp(load.getId(), actorId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("assigned driver");

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.DISPATCHED);
    verify(employeeService, never()).getEntityById(any(UUID.class));
    verify(loadRepository, never()).save(any(Load.class));
  }

  @Test
  void unmappedActorIsDeniedBeforeSave() {
    Employee assigned = employee();
    Load load = loadWithTruck(assigned, null, LoadStatus.DISPATCHED);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    when(employeeService.getEntityById(assigned.getId()))
        .thenThrow(new ResourceNotFoundException("Employee not found: " + assigned.getId()));

    assertThatThrownBy(() -> loadService.pickUp(load.getId(), assigned.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unavailable");

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.DISPATCHED);
    verify(loadRepository, never()).save(any(Load.class));
  }

  @Test
  void authorizedDriverStillGetsStateMachineErrorWithoutSaving() {
    Employee driver = employee();
    Load load = loadWithTruck(driver, null, LoadStatus.DRAFT);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    when(employeeService.getEntityById(driver.getId())).thenReturn(driver);

    assertThatThrownBy(() -> loadService.pickUp(load.getId(), driver.getId()))
        .isInstanceOf(InvalidStateTransitionException.class);

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.DRAFT);
    verify(loadRepository, never()).save(any(Load.class));
  }

  @Test
  void explicitOperatorPermissionCanBypassAssignment() {
    Employee operator = employee();
    TenantRole role = new TenantRole();
    TenantRoleClaim claim = new TenantRoleClaim();
    claim.setClaimType("permission");
    claim.setClaimValue("load.confirm_status");
    role.setClaims(java.util.List.of(claim));
    operator.setRole(role);
    Load load = loadWithTruck(employee(), null, LoadStatus.DISPATCHED);
    when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
    when(loadRepository.save(load)).thenReturn(load);
    when(employeeService.getEntityById(operator.getId())).thenReturn(operator);

    loadService.pickUp(load.getId(), operator.getId());

    assertThat(load.getStatusEnum()).isEqualTo(LoadStatus.PICKED_UP);
    ArgumentCaptor<Load> saved = ArgumentCaptor.forClass(Load.class);
    verify(loadRepository).save(saved.capture());
    assertThat(saved.getValue().getStatusEnum()).isEqualTo(LoadStatus.PICKED_UP);
  }

  private static Load loadWithTruck(Employee main, Employee secondary, LoadStatus status) {
    Load load = new Load();
    load.setId(UUID.randomUUID());
    load.setStatusEnum(status);
    Truck truck = new Truck();
    truck.setMainDriver(main);
    truck.setSecondaryDriver(secondary);
    load.setAssignedTruck(truck);
    return load;
  }

  private static Employee employee() {
    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    return employee;
  }
}
