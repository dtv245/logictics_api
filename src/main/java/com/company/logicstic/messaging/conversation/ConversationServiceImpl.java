package com.company.logicstic.messaging.conversation;

import com.company.logicstic.employee.employee.Employee;
import com.company.logicstic.employee.employee.EmployeeService;
import com.company.logicstic.load.core.LoadService;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.shared.persistence.AbstractBaseService;
import com.company.logicstic.shared.web.PagedResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class ConversationServiceImpl
    extends AbstractBaseService<Conversation, ConversationResponse, CreateConversationRequest>
    implements ConversationService {

  private final ConversationRepository conversationRepository;
  private final LoadService loadService;
  private final EmployeeService employeeService;
  private final ConversationMapper conversationMapper;

  public ConversationServiceImpl(
      ConversationRepository conversationRepository,
      LoadService loadService,
      EmployeeService employeeService,
      ConversationMapper conversationMapper) {
    super(
        conversationRepository,
        conversationMapper::toResponse,
        conversationMapper::toEntity,
        conversationMapper::updateEntity);
    this.conversationRepository = conversationRepository;
    this.loadService = loadService;
    this.employeeService = employeeService;
    this.conversationMapper = conversationMapper;
  }

  @Override
  protected String entityName() {
    return "Conversation";
  }

  public PagedResponse<ConversationResponse> listByParticipant(
      UUID employeeId, int page, int pageSize) {
    var pageable = PageRequest.of(page - 1, pageSize, Sort.by("lastMessageAt").descending());
    return PagedResponse.from(
        conversationRepository
            .findByParticipant(employeeId, pageable)
            .map(conversationMapper::toResponse));
  }

  @Override
  public ConversationResponse getByIdForParticipant(UUID conversationId, UUID employeeId) {
    return conversationRepository
        .findByIdAndParticipant(conversationId, employeeId)
        .map(conversationMapper::toResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("Conversation not found: " + conversationId));
  }

  @Override
  @Transactional
  public ConversationResponse createForParticipant(
      CreateConversationRequest request, UUID currentEmployeeId) {
    Set<UUID> participantIds = new LinkedHashSet<>(request.participantIds());
    participantIds.add(currentEmployeeId);
    CreateConversationRequest trustedRequest =
        new CreateConversationRequest(
            request.name(), request.loadId(), request.isTenantChat(), Set.copyOf(participantIds));
    return super.create(trustedRequest);
  }

  @Override
  protected void beforeCreate(Conversation conversation, CreateConversationRequest request) {
    resolveLoad(conversation, request);
    rebuildParticipants(conversation, request);
  }

  @Override
  protected void beforeUpdate(Conversation conversation, CreateConversationRequest request) {
    resolveLoad(conversation, request);
    rebuildParticipants(conversation, request);
  }

  private void resolveLoad(Conversation conversation, CreateConversationRequest request) {
    if (request.loadId() != null) {
      conversation.setLoad(loadService.getEntityById(request.loadId()));
    } else {
      conversation.setLoad(null);
    }
  }

  private void rebuildParticipants(Conversation conversation, CreateConversationRequest request) {
    conversation.getParticipants().clear();
    OffsetDateTime joinedAt = OffsetDateTime.now();
    for (UUID employeeId : request.participantIds()) {
      Employee employee = employeeService.getEntityById(employeeId);
      ConversationParticipant participant = new ConversationParticipant();
      participant.setConversation(conversation);
      participant.setEmployee(employee);
      participant.setJoinedAt(joinedAt);
      participant.setIsMuted(false);
      conversation.getParticipants().add(participant);
    }
  }
}
