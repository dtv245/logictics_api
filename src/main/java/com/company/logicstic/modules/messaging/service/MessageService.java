package com.company.logicstic.modules.messaging.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.messaging.dto.MessageView;
import com.company.logicstic.modules.messaging.dto.SendMessageRequest;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.entity.Message;
import com.company.logicstic.modules.messaging.mapper.MessageMapper;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.repository.MessageRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class MessageService extends AbstractBaseService<Message, MessageView, SendMessageRequest> {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final EmployeeRepository employeeRepository;
    private final MessageMapper messageMapper;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          EmployeeRepository employeeRepository,
                          MessageMapper messageMapper) {
        super(messageRepository, messageMapper::toView, messageMapper::toEntity, (req, entity) -> {});
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.employeeRepository = employeeRepository;
        this.messageMapper = messageMapper;
    }

    @Override
    protected String entityName() {
        return "Message";
    }

    public PagedResponse<MessageView> listByConversation(UUID conversationId, int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("sentAt").ascending());
        return PagedResponse.from(
                messageRepository.findByConversationIdOrderBySentAtAsc(conversationId, pageable)
                        .map(messageMapper::toView)
        );
    }

    @Override
    protected void beforeCreate(Message message, SendMessageRequest request) {
        resolveRelations(message, request);
    }

    public long countUnread(UUID employeeId) {
        return messageRepository.countUnread(employeeId);
    }

    private void resolveRelations(Message message, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + request.conversationId()));
        message.setConversation(conversation);

        Employee sender = employeeRepository.findById(request.senderId())
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found: " + request.senderId()));
        message.setSender(sender);
    }
}