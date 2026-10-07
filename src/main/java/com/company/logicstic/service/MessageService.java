package com.company.logicstic.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.message.MessageView;
import com.company.logicstic.dto.message.SendMessageRequest;
import com.company.logicstic.entity.Conversation;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Message;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.ConversationParticipantRepository;
import com.company.logicstic.repository.ConversationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.MessageRepository;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentUserService currentUserService;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository conversationParticipantRepository,
                          EmployeeRepository employeeRepository,
                          CurrentUserService currentUserService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.employeeRepository = employeeRepository;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<MessageView> listByConversation(UUID conversationId, int page, int pageSize) {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        validateParticipantAccess(conversation, identity.employeeId());

        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("sentAt").ascending());
        return PagedResponse.from(
                messageRepository.findByConversationIdOrderBySentAtAsc(conversationId, pageable)
                        .map(MessageView::from)
        );
    }

    @Transactional
    public MessageView send(SendMessageRequest request) {
        CurrentUserResponse currentUser = currentUserService.requireMappedEmployee();
        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + request.conversationId()));

        if (request.senderId() != null && !request.senderId().equals(currentUser.employeeId())) {
            throw new ForbiddenException("Cannot send message on behalf of another user");
        }

        validateParticipantAccess(conversation, currentUser.employeeId());

        Employee sender = employeeRepository.findById(currentUser.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found: " + currentUser.employeeId()));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        message.setSentAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        message.setIsDeleted(false);

        conversation.setLastMessageAt(OffsetDateTime.now());

        return MessageView.from(messageRepository.save(message));
    }

    public long countUnread(UUID employeeId) {
        CurrentUserResponse currentUser = currentUserService.requireMappedEmployee();
        if (!currentUser.employeeId().equals(employeeId)) {
            throw new ForbiddenException("Cannot access unread count of another user");
        }
        return messageRepository.countUnread(employeeId);
    }

    private void validateParticipantAccess(Conversation conversation) {
        CurrentUserResponse currentUser = currentUserService.requireMappedEmployee();
        validateParticipantAccess(conversation, currentUser.employeeId());
    }

    private void validateParticipantAccess(Conversation conversation, UUID employeeId) {
        if (Boolean.TRUE.equals(conversation.getIsTenantChat())) {
            return;
        }
        boolean isParticipant = conversationParticipantRepository
                .existsByConversationIdAndEmployeeId(conversation.getId(), employeeId);
        if (!isParticipant) {
            throw new ForbiddenException("User is not a participant in this conversation");
        }
    }
}
