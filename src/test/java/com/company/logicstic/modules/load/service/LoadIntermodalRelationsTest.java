package com.company.logicstic.modules.load.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.customer.entity.Customer;
import com.company.logicstic.modules.customer.service.CustomerService;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.fleet.entity.Container;
import com.company.logicstic.modules.fleet.service.ContainerService;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.mapper.LoadMapper;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.load.service.impl.LoadServiceImpl;
import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.modules.terminal.service.TerminalService;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class LoadIntermodalRelationsTest {

  @Test
  void createResolvesAndReturnsContainerAndTerminalIds() {
    UUID customerId = UUID.randomUUID();
    UUID containerId = UUID.randomUUID();
    UUID originTerminalId = UUID.randomUUID();
    UUID destinationTerminalId = UUID.randomUUID();
    Customer customer = new Customer();
    customer.setId(customerId);
    Container container = new Container();
    container.setId(containerId);
    Terminal origin = new Terminal();
    origin.setId(originTerminalId);
    Terminal destination = new Terminal();
    destination.setId(destinationTerminalId);

    LoadRepository loadRepository =
        proxy(
            LoadRepository.class,
            (method, args) -> {
              if (method.equals("save")) {
                return args[0];
              }
              throw new AssertionError("Unexpected LoadRepository call: " + method);
            });
    CustomerService customerService = findByIdService(CustomerService.class, customerId, customer);
    ContainerService containerService =
        findByIdService(ContainerService.class, containerId, container);
    TerminalService terminalService =
        proxy(
            TerminalService.class,
            (method, args) -> {
              if (!method.equals("getEntityById")) {
                throw new AssertionError("Unexpected TerminalService call: " + method);
              }
              return args[0].equals(originTerminalId) ? origin : destination;
            });
    LoadMapper mapper = Mappers.getMapper(LoadMapper.class);
    LoadService service =
        new LoadServiceImpl(
            loadRepository,
            customerService,
            unused(TruckService.class),
            containerService,
            terminalService,
            unused(EmployeeService.class),
            event -> {},
            mapper);

    LoadResponse view =
        service.create(request(customerId, containerId, originTerminalId, destinationTerminalId));

    assertThat(view.containerId()).isEqualTo(containerId);
    assertThat(view.originTerminalId()).isEqualTo(originTerminalId);
    assertThat(view.destinationTerminalId()).isEqualTo(destinationTerminalId);
  }

  @Test
  void createRejectsNonDraftStatusBeforeResolvingRelations() {
    LoadService service = serviceWithLoadRepository(unused(LoadRepository.class));
    CreateLoadRequest request = request(UUID.randomUUID(), null, null, null, "Dispatched");

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void updateRejectsStatusChangesOutsideTransitionEndpoints() {
    UUID id = UUID.randomUUID();
    Load existing = new Load();
    existing.setId(id);
    existing.setStatus("draft");
    LoadRepository loadRepository =
        proxy(
            LoadRepository.class,
            (method, args) -> {
              if (method.equals("findById")) {
                return Optional.of(existing);
              }
              throw new AssertionError("Unexpected LoadRepository call: " + method);
            });
    LoadService service = serviceWithLoadRepository(loadRepository);
    CreateLoadRequest request = request(UUID.randomUUID(), null, null, null, "Delivered");

    assertThatThrownBy(() -> service.update(id, request))
        .isInstanceOf(InvalidStateTransitionException.class);
    assertThat(existing.getStatus()).isEqualTo("draft");
  }

  private CreateLoadRequest request(
      UUID customerId, UUID containerId, UUID originTerminalId, UUID destinationTerminalId) {
    return request(customerId, containerId, originTerminalId, destinationTerminalId, "Draft");
  }

  private CreateLoadRequest request(
      UUID customerId,
      UUID containerId,
      UUID originTerminalId,
      UUID destinationTerminalId,
      String status) {
    return new CreateLoadRequest(
        "Intermodal load",
        "CONTAINER",
        status,
        100.0,
        false,
        customerId,
        null,
        null,
        "MANUAL",
        null,
        null,
        null,
        false,
        null,
        null,
        containerId,
        originTerminalId,
        destinationTerminalId,
        null,
        null,
        null,
        new BigDecimal("100.00"),
        "USD",
        "Origin street",
        null,
        "Origin city",
        "OS",
        "10000",
        "US",
        1.0,
        2.0,
        "Destination street",
        null,
        "Destination city",
        "DS",
        "20000",
        "US",
        3.0,
        4.0);
  }

  private LoadService serviceWithLoadRepository(LoadRepository loadRepository) {
    return new LoadServiceImpl(
        loadRepository,
        unused(CustomerService.class),
        unused(TruckService.class),
        unused(ContainerService.class),
        unused(TerminalService.class),
        unused(EmployeeService.class),
        event -> {},
        Mappers.getMapper(LoadMapper.class));
  }

  /** Stubs the owning feature's service so it resolves the association to {@code entity}. */
  private static <T> T findByIdService(Class<T> type, UUID id, Object entity) {
    return proxy(
        type,
        (method, args) -> {
          if (method.equals("getEntityById")) {
            assertThat(args[0]).isEqualTo(id);
            return entity;
          }
          throw new AssertionError("Unexpected " + type.getSimpleName() + " call: " + method);
        });
  }

  private static <T> T findByIdRepository(Class<T> type, UUID id, Object entity) {
    return proxy(
        type,
        (method, args) -> {
          if (method.equals("findById")) {
            assertThat(args[0]).isEqualTo(id);
            return Optional.of(entity);
          }
          throw new AssertionError("Unexpected " + type.getSimpleName() + " call: " + method);
        });
  }

  private static <T> T unused(Class<T> type) {
    return proxy(
        type,
        (method, args) -> {
          throw new AssertionError("Unexpected " + type.getSimpleName() + " call: " + method);
        });
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
