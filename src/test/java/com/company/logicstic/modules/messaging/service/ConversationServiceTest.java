package com.company.logicstic.modules.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.entity.ConversationParticipant;
import com.company.logicstic.modules.messaging.mapper.ConversationMapper;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.service.impl.ConversationServiceImpl;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
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

  @Test
  void createForParticipantAddsAuthenticatedEmployeeExactlyOnce() {
    UUID currentId = UUID.randomUUID();
    UUID invitedId = UUID.randomUUID();
    Employee current = employee(currentId);
    Employee invited = employee(invitedId);
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
                return args[0].equals(currentId) ? current : invited;
              }
              throw new AssertionError("Unexpected EmployeeService call: " + method);
            });
    ConversationService service =
        new ConversationServiceImpl(
            conversationRepository,
            unused(LoadService.class),
            employeeService,
            Mappers.getMapper(ConversationMapper.class));

    var view =
        service.createForParticipant(
            new CreateConversationRequest("Dispatch", null, false, Set.of(invitedId, currentId)),
            currentId);

    assertThat(view.participantIds()).containsExactlyInAnyOrder(currentId, invitedId);
  }

  @Test
  void getByIdForParticipantUsesScopedRepositoryResult() {
    UUID conversationId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    Conversation conversation = conversation(conversationId, employee(employeeId));
    ConversationRepository conversationRepository =
        proxy(
            ConversationRepository.class,
            (method, args) -> {
              if (method.equals("findByIdAndParticipant")) {
                return Optional.of(conversation);
              }
              throw new AssertionError("Unexpected ConversationRepository call: " + method);
            });
    ConversationService service =
        new ConversationServiceImpl(
            conversationRepository,
            unused(LoadService.class),
            unused(EmployeeService.class),
            Mappers.getMapper(ConversationMapper.class));

    assertThat(service.getByIdForParticipant(conversationId, employeeId).id())
        .isEqualTo(conversationId);
  }

  @Test
  void getByIdForParticipantHidesMissingOrUnauthorizedConversation() {
    UUID conversationId = UUID.randomUUID();
    ConversationRepository conversationRepository =
        proxy(
            ConversationRepository.class,
            (method, args) -> {
              if (method.equals("findByIdAndParticipant")) {
                return Optional.empty();
              }
              throw new AssertionError("Unexpected ConversationRepository call: " + method);
            });
    ConversationService service =
        new ConversationServiceImpl(
            conversationRepository,
            unused(LoadService.class),
            unused(EmployeeService.class),
            Mappers.getMapper(ConversationMapper.class));

    assertThatThrownBy(() -> service.getByIdForParticipant(conversationId, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Conversation not found: " + conversationId);
  }

  private Employee employee(UUID id) {
    Employee employee = new Employee();
    employee.setId(id);
    return employee;
  }

  private Conversation conversation(UUID id, Employee employee) {
    Conversation conversation = new Conversation();
    conversation.setId(id);
    conversation.setName("Dispatch");
    conversation.setIsTenantChat(false);
    conversation.setCreatedAt(OffsetDateTime.now());
    ConversationParticipant participant = new ConversationParticipant();
    participant.setConversation(conversation);
    participant.setEmployee(employee);
    conversation.getParticipants().add(participant);
    return conversation;
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
