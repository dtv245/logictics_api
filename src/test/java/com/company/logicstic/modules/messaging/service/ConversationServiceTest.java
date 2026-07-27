package com.company.logicstic.modules.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.mapper.ConversationMapper;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.service.impl.ConversationServiceImpl;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ConversationServiceTest {

  @Test
  void createBuildsRequiredParticipants() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Employee first = employee(firstId);
    Employee second = employee(secondId);
    ConversationRepository conversationRepository =
        proxy(
            ConversationRepository.class,
            (method, args) -> {
              if (method.equals("save")) {
                return args[0];
              }
              throw new AssertionError("Unexpected ConversationRepository call: " + method);
            });
    EmployeeService employeeService =
        proxy(
            EmployeeService.class,
            (method, args) -> {
              if (method.equals("getEntityById")) {
                return args[0].equals(firstId) ? first : second;
              }
              throw new AssertionError("Unexpected EmployeeService call: " + method);
            });
    ConversationMapper mapper = Mappers.getMapper(ConversationMapper.class);
    ConversationService service =
        new ConversationServiceImpl(
            conversationRepository, unused(LoadService.class), employeeService, mapper);

    var view =
        service.create(
            new CreateConversationRequest("Dispatch", null, false, Set.of(firstId, secondId)));

    assertThat(view.participantIds()).containsExactlyInAnyOrder(firstId, secondId);
  }

  private Employee employee(UUID id) {
    Employee employee = new Employee();
    employee.setId(id);
    return employee;
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
