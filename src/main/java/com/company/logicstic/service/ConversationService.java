package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.message.ConversationView;
import com.company.logicstic.dto.message.CreateConversationRequest;
import com.company.logicstic.entity.Conversation;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.ConversationRepository;
import com.company.logicstic.repository.LoadRepository;

@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final LoadRepository loadRepository;

    public ConversationService(ConversationRepository conversationRepository, LoadRepository loadRepository) {
        this.conversationRepository = conversationRepository;
        this.loadRepository = loadRepository;
    }

    public PagedResponse<ConversationView> listByParticipant(UUID employeeId, int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("lastMessageAt").descending());
        return PagedResponse.from(conversationRepository.findByParticipant(employeeId, pageable)
                .map(ConversationView::from));
    }

    public ConversationView getById(UUID id) {
        return conversationRepository.findById(id)
                .map(ConversationView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + id));
    }

    @Transactional
    public ConversationView create(CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setName(request.name());
        conversation.setIsTenantChat(request.isTenantChat());

        if (request.loadId() != null) {
            Load load = loadRepository.findById(request.loadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + request.loadId()));
            conversation.setLoad(load);
        }

        return ConversationView.from(conversationRepository.save(conversation));
    }
}