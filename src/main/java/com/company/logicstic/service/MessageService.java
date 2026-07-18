package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.message.MessageView;
import com.company.logicstic.dto.message.SendMessageRequest;
import com.company.logicstic.entity.Conversation;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Message;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.ConversationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.MessageRepository;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final EmployeeRepository employeeRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          EmployeeRepository employeeRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.employeeRepository = employeeRepository;
    }

    public PagedResponse<MessageView> listByConversation(UUID conversationId, int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("sentAt").ascending());
        return PagedResponse.from(
                messageRepository.findByConversationIdOrderBySentAtAsc(conversationId, pageable)
                        .map(MessageView::from)
        );
    }

    @Transactional
    public MessageView send(SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + request.conversationId()));

        Employee sender = employeeRepository.findById(request.senderId())
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found: " + request.senderId()));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());

        return MessageView.from(messageRepository.save(message));
    }

    public long countUnread(UUID employeeId) {
        return messageRepository.countUnread(employeeId);
    }
}