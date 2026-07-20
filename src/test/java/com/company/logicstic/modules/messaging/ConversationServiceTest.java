package com.company.logicstic.modules.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.messaging.dto.CreateConversationRequest;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.mapper.ConversationMapper;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.service.ConversationService;
import java.lang.reflect.Proxy;
import java.util.Optional;
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
    EmployeeRepository employeeRepository =
        proxy(
            EmployeeRepository.class,
            (method, args) -> {
              if (method.equals("findById")) {
                return Optional.of(args[0].equals(firstId) ? first : second);
              }
              throw new AssertionError("Unexpected EmployeeRepository call: " + method);
            });
    ConversationMapper mapper = Mappers.getMapper(ConversationMapper.class);
    ConversationService service =
        new ConversationService(
            conversationRepository, unused(LoadRepository.class), employeeRepository, mapper);

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
