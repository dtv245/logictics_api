package com.company.logicstic.modules.messaging.service;

import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.dto.response.ConversationResponse;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;
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
}
