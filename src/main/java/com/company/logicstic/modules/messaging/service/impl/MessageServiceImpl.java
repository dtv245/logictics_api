package com.company.logicstic.modules.messaging.service.impl;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.dto.response.MessageResponse;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.entity.Message;
import com.company.logicstic.modules.messaging.entity.MessageReadReceipt;
import com.company.logicstic.modules.messaging.mapper.MessageMapper;
import com.company.logicstic.modules.messaging.repository.ConversationParticipantRepository;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.repository.MessageReadReceiptRepository;
import com.company.logicstic.modules.messaging.repository.MessageRepository;
import com.company.logicstic.modules.messaging.service.MessageService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class MessageServiceImpl
    extends AbstractBaseService<Message, MessageResponse, SendMessageRequest>
    implements MessageService {

  private final MessageRepository messageRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationParticipantRepository participantRepository;
  private final MessageReadReceiptRepository readReceiptRepository;
  private final EmployeeService employeeService;
  private final MessageMapper messageMapper;

  public MessageServiceImpl(
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      ConversationParticipantRepository participantRepository,
      MessageReadReceiptRepository readReceiptRepository,
      EmployeeService employeeService,
      MessageMapper messageMapper) {
    super(
        messageRepository, messageMapper::toResponse, messageMapper::toEntity, (req, entity) -> {});
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.participantRepository = participantRepository;
    this.readReceiptRepository = readReceiptRepository;
    this.employeeService = employeeService;
    this.messageMapper = messageMapper;
  }

  @Override
  protected String entityName() {
    return "Message";
  }

  public PagedResponse<MessageResponse> listByConversation(
      UUID conversationId, int page, int pageSize) {
    var pageable = PageRequest.of(page - 1, pageSize, Sort.by("sentAt").ascending());
    return PagedResponse.from(
        messageRepository
            .findByConversationIdOrderBySentAtAsc(conversationId, pageable)
            .map(messageMapper::toResponse));
  }

  @Override
  public PagedResponse<MessageResponse> listByConversationForParticipant(
      UUID conversationId, UUID employeeId, int page, int pageSize) {
    requireParticipant(conversationId, employeeId);
    return listByConversation(conversationId, page, pageSize);
  }

  @Override
  @Transactional
  public MessageResponse sendAsParticipant(UUID employeeId, SendMessageRequest request) {
    if (!employeeId.equals(request.senderId())) {
      throw new ApiException(
          ErrorCode.ACCESS_DENIED, "Message sender does not match the authenticated employee");
    }
    SendMessageRequest trustedRequest =
        new SendMessageRequest(request.conversationId(), employeeId, request.content());
    return super.create(trustedRequest);
  }

  @Override
  protected void beforeCreate(Message message, SendMessageRequest request) {
    resolveRelations(message, request);
    OffsetDateTime sentAt = OffsetDateTime.now();
    message.setSentAt(sentAt);
    message.setIsDeleted(false);
    message.setDeletedAt(null);
    message.getConversation().setLastMessageAt(sentAt);
  }

  public long countUnread(UUID employeeId) {
    return messageRepository.countUnread(employeeId);
  }

  @Transactional
  public int markRead(UUID conversationId, UUID employeeId) {
    var participant =
        participantRepository
            .findByConversationIdAndEmployeeId(conversationId, employeeId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Conversation participant not found: " + employeeId));
    Employee employee = participant.getEmployee();
    OffsetDateTime readAt = OffsetDateTime.now();
    var unreadMessages = messageRepository.findUnread(conversationId, employeeId);
    var receipts =
        unreadMessages.stream()
            .map(
                message -> {
                  MessageReadReceipt receipt = new MessageReadReceipt();
                  receipt.setMessage(message);
                  receipt.setReadBy(employee);
                  receipt.setReadAt(readAt);
                  return receipt;
                })
            .toList();
    if (!receipts.isEmpty()) {
      readReceiptRepository.saveAll(receipts);
    }
    participant.setLastReadAt(readAt);
    participantRepository.save(participant);
    return receipts.size();
  }

  private void resolveRelations(Message message, SendMessageRequest request) {
    Conversation conversation =
        conversationRepository
            .findById(request.conversationId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Conversation not found: " + request.conversationId()));
    message.setConversation(conversation);

    requireParticipant(request.conversationId(), request.senderId());

    Employee sender = employeeService.getEntityById(request.senderId());
    message.setSender(sender);
  }

  private void requireParticipant(UUID conversationId, UUID employeeId) {
    if (!participantRepository.existsByConversationIdAndEmployeeId(conversationId, employeeId)) {
      throw new ResourceNotFoundException("Conversation not found: " + conversationId);
    }
  }
}
