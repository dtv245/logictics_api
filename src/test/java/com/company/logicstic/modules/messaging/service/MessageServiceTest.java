package com.company.logicstic.modules.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.dto.response.MessageResponse;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.entity.ConversationParticipant;
import com.company.logicstic.modules.messaging.entity.Message;
import com.company.logicstic.modules.messaging.entity.MessageReadReceipt;
import com.company.logicstic.modules.messaging.mapper.MessageMapper;
import com.company.logicstic.modules.messaging.repository.ConversationParticipantRepository;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.repository.MessageReadReceiptRepository;
import com.company.logicstic.modules.messaging.repository.MessageRepository;
import com.company.logicstic.modules.messaging.service.impl.MessageServiceImpl;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MessageServiceTest {

  @Test
  void createInitializesRequiredFieldsAndAdvancesConversationTimestamp() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    SendMessageRequest request = new SendMessageRequest(conversationId, senderId, "hello");
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    Employee sender = new Employee();
    sender.setId(senderId);
    AtomicReference<Message> saved = new AtomicReference<>();

    MessageRepository messageRepository =
        proxy(
            MessageRepository.class,
            (method, args) -> {
              if (method.equals("save")) {
                Message message = (Message) args[0];
                saved.set(message);
                return message;
              }
              throw new AssertionError("Unexpected MessageRepository call: " + method);
            });
    ConversationRepository conversationRepository =
        proxy(
            ConversationRepository.class,
            (method, args) -> {
              if (method.equals("findById")) {
                return Optional.of(conversation);
              }
              throw new AssertionError("Unexpected ConversationRepository call: " + method);
            });
    EmployeeService employeeService =
        proxy(
            EmployeeService.class,
            (method, args) -> {
              if (method.equals("getEntityById")) {
                return sender;
              }
              throw new AssertionError("Unexpected EmployeeService call: " + method);
            });
    MessageMapper messageMapper = Mappers.getMapper(MessageMapper.class);
    ConversationParticipantRepository participantRepository =
        proxy(
            ConversationParticipantRepository.class,
            (method, args) -> {
              if (method.equals("existsByConversationIdAndEmployeeId")) {
                return true;
              }
              throw new AssertionError(
                  "Unexpected ConversationParticipantRepository call: " + method);
            });
    MessageService service =
        new MessageServiceImpl(
            messageRepository,
            conversationRepository,
            participantRepository,
            unused(MessageReadReceiptRepository.class),
            employeeService,
            messageMapper);

    OffsetDateTime before = OffsetDateTime.now();
    MessageResponse view = service.sendAsParticipant(senderId, request);
    Message message = saved.get();

    assertThat(view.content()).isEqualTo("hello");
    assertThat(message).isNotNull();
    assertThat(message.getConversation()).isSameAs(conversation);
    assertThat(message.getSender()).isSameAs(sender);
    assertThat(message.getSentAt()).isAfterOrEqualTo(before);
    assertThat(message.getIsDeleted()).isFalse();
    assertThat(message.getDeletedAt()).isNull();
    assertThat(conversation.getLastMessageAt()).isEqualTo(message.getSentAt());
  }

  @Test
  void sendAsParticipantRejectsMismatchedLegacySenderBeforeDataAccess() {
    UUID currentEmployeeId = UUID.randomUUID();
    SendMessageRequest request =
        new SendMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "forged");
    MessageService service =
        new MessageServiceImpl(
            unused(MessageRepository.class),
            unused(ConversationRepository.class),
            unused(ConversationParticipantRepository.class),
            unused(MessageReadReceiptRepository.class),
            unused(EmployeeService.class),
            Mappers.getMapper(MessageMapper.class));

    assertThatThrownBy(() -> service.sendAsParticipant(currentEmployeeId, request))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus().value()).isEqualTo(403);
              assertThat(exception.getCode()).isEqualTo("ACCESS_DENIED");
            });
  }

  @Test
  void listByConversationHidesConversationFromNonParticipant() {
    UUID conversationId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    ConversationParticipantRepository participantRepository =
        proxy(
            ConversationParticipantRepository.class,
            (method, args) -> {
              if (method.equals("existsByConversationIdAndEmployeeId")) {
                return false;
              }
              throw new AssertionError(
                  "Unexpected ConversationParticipantRepository call: " + method);
            });
    MessageService service =
        new MessageServiceImpl(
            unused(MessageRepository.class),
            unused(ConversationRepository.class),
            participantRepository,
            unused(MessageReadReceiptRepository.class),
            unused(EmployeeService.class),
            Mappers.getMapper(MessageMapper.class));

    assertThatThrownBy(
            () -> service.listByConversationForParticipant(conversationId, employeeId, 1, 50))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Conversation not found: " + conversationId);
  }

  @Test
  void markReadCreatesReceiptsAndUpdatesParticipantTimestamp() {
    UUID conversationId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    Employee employee = new Employee();
    employee.setId(employeeId);
    ConversationParticipant participant = new ConversationParticipant();
    participant.setEmployee(employee);
    Message first = new Message();
    Message second = new Message();
    AtomicReference<List<MessageReadReceipt>> savedReceipts = new AtomicReference<>();

    MessageRepository messageRepository =
        proxy(
            MessageRepository.class,
            (method, args) -> {
              if (method.equals("findUnread")) {
                return List.of(first, second);
              }
              throw new AssertionError("Unexpected MessageRepository call: " + method);
            });
    ConversationParticipantRepository participantRepository =
        proxy(
            ConversationParticipantRepository.class,
            (method, args) -> {
              if (method.equals("findByConversationIdAndEmployeeId")) {
                return Optional.of(participant);
              }
              if (method.equals("save")) {
                return args[0];
              }
              throw new AssertionError(
                  "Unexpected ConversationParticipantRepository call: " + method);
            });
    MessageReadReceiptRepository receiptRepository =
        proxy(
            MessageReadReceiptRepository.class,
            (method, args) -> {
              if (method.equals("saveAll")) {
                savedReceipts.set((List<MessageReadReceipt>) args[0]);
                return args[0];
              }
              throw new AssertionError("Unexpected MessageReadReceiptRepository call: " + method);
            });
    MessageService service =
        new MessageServiceImpl(
            messageRepository,
            unused(ConversationRepository.class),
            participantRepository,
            receiptRepository,
            unused(EmployeeService.class),
            Mappers.getMapper(MessageMapper.class));

    assertThat(service.markRead(conversationId, employeeId)).isEqualTo(2);
    assertThat(participant.getLastReadAt()).isNotNull();
    assertThat(savedReceipts.get()).hasSize(2);
    assertThat(savedReceipts.get())
        .allSatisfy(
            receipt -> {
              assertThat(receipt.getReadBy()).isSameAs(employee);
              assertThat(receipt.getReadAt()).isEqualTo(participant.getLastReadAt());
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
