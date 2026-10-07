package com.company.logicstic.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.message.ConversationView;
import com.company.logicstic.dto.message.CreateConversationRequest;
import com.company.logicstic.entity.Conversation;
import com.company.logicstic.entity.ConversationParticipant;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.ConversationParticipantRepository;
import com.company.logicstic.repository.ConversationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;

@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final LoadRepository loadRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentUserService currentUserService;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository conversationParticipantRepository,
                               LoadRepository loadRepository,
                               EmployeeRepository employeeRepository,
                               CurrentUserService currentUserService) {
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.loadRepository = loadRepository;
        this.employeeRepository = employeeRepository;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<ConversationView> listByParticipant(UUID employeeId, int page, int pageSize) {
        CurrentUserResponse currentUser = currentUserService.requireMappedEmployee();
        if (!currentUser.employeeId().equals(employeeId)) {
            throw new ForbiddenException("Cannot access conversations of another user");
        }
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("lastMessageAt").descending());
        return PagedResponse.from(conversationRepository.findByParticipant(employeeId, pageable)
                .map(ConversationView::from));
    }

    public ConversationView getById(UUID id) {
        CurrentUserResponse currentUser = currentUserService.requireMappedEmployee();
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + id));

        if (!Boolean.TRUE.equals(conversation.getIsTenantChat())
                && !conversationParticipantRepository.existsByConversationIdAndEmployeeId(id, currentUser.employeeId())) {
            throw new ForbiddenException("User is not a participant in this conversation");
        }

        return ConversationView.from(conversation);
    }

    @Transactional
    public ConversationView create(CreateConversationRequest request) {
        CurrentUserResponse currentUser = currentUserService.requireMappedEmployee();
        if (Boolean.TRUE.equals(request.isTenantChat()) && !currentUser.roles().contains("ADMIN"))
            throw new ForbiddenException("Only ADMIN may create tenant conversations");

        Conversation conversation = new Conversation();
        conversation.setName(request.name());
        conversation.setIsTenantChat(request.isTenantChat());

        if (request.loadId() != null) {
            Load load = loadRepository.findById(request.loadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + request.loadId()));
            conversation.setLoad(load);
        }

        Employee currentEmployee = employeeRepository.findById(currentUser.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + currentUser.employeeId()));

        ConversationParticipant creatorParticipant = new ConversationParticipant();
        creatorParticipant.setConversation(conversation);
        creatorParticipant.setEmployee(currentEmployee);
        creatorParticipant.setJoinedAt(OffsetDateTime.now());
        creatorParticipant.setIsMuted(false);
        conversation.getParticipants().add(creatorParticipant);

        return ConversationView.from(conversationRepository.save(conversation));
    }
}
