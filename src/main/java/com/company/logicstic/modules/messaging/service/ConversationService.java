package com.company.logicstic.modules.messaging.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.messaging.dto.ConversationView;
import com.company.logicstic.modules.messaging.dto.CreateConversationRequest;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.mapper.ConversationMapper;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class ConversationService extends AbstractBaseService<Conversation, ConversationView, CreateConversationRequest> {

    private final ConversationRepository conversationRepository;
    private final LoadRepository loadRepository;
    private final ConversationMapper conversationMapper;

    public ConversationService(ConversationRepository conversationRepository, LoadRepository loadRepository,
                                ConversationMapper conversationMapper) {
        super(conversationRepository, conversationMapper::toView, conversationMapper::toEntity, conversationMapper::updateEntity);
        this.conversationRepository = conversationRepository;
        this.loadRepository = loadRepository;
        this.conversationMapper = conversationMapper;
    }

    @Override
    protected String entityName() {
        return "Conversation";
    }

    public PagedResponse<ConversationView> listByParticipant(UUID employeeId, int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("lastMessageAt").descending());
        return PagedResponse.from(conversationRepository.findByParticipant(employeeId, pageable)
                .map(conversationMapper::toView));
    }

    @Override
    protected void beforeCreate(Conversation conversation, CreateConversationRequest request) {
        if (request.loadId() != null) {
            conversation.setLoad(loadRepository.findById(request.loadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + request.loadId())));
        }
    }
}