package com.company.logicstic.modules.messaging.service;

import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.dto.response.MessageResponse;
import com.company.logicstic.modules.messaging.entity.Message;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;
import java.util.UUID;

/** Public API of the message feature — messages inside a conversation and their read receipts. */
public interface MessageService extends CrudService<Message, MessageResponse, SendMessageRequest> {

  /**
   * Lists the messages of a conversation, newest first.
   *
   * @param page 1-based page number
   */
  PagedResponse<MessageResponse> listByConversation(UUID conversationId, int page, int pageSize);

  /** Counts messages the employee has not read yet, across all their conversations. */
  long countUnread(UUID employeeId);

  /**
   * Marks every unread message of a conversation as read by the employee.
   *
   * @return the number of messages newly marked as read
   */
  int markRead(UUID conversationId, UUID employeeId);
}
