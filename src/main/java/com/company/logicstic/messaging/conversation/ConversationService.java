package com.company.logicstic.messaging.conversation;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;
import java.util.UUID;

/**
 * Public API of the conversation feature — direct, tenant-wide and load-scoped chat threads ({@code
 * docs/docs/business-spec.md} §6.2).
 */
public interface ConversationService
    extends CrudService<Conversation, ConversationResponse, CreateConversationRequest> {

  /**
   * Lists the conversations an employee participates in.
   *
   * @param page 1-based page number
   */
  PagedResponse<ConversationResponse> listByParticipant(UUID employeeId, int page, int pageSize);

  /** Returns a conversation only when the employee is an explicit participant. */
  ConversationResponse getByIdForParticipant(UUID conversationId, UUID employeeId);

  /** Creates a conversation and adds the authenticated employee to its participant set. */
  ConversationResponse createForParticipant(
      CreateConversationRequest request, UUID currentEmployeeId);
}
