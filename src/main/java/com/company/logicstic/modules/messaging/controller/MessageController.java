package com.company.logicstic.modules.messaging.controller;

import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.dto.response.ConversationResponse;
import com.company.logicstic.modules.messaging.dto.response.MessageResponse;
import com.company.logicstic.modules.messaging.service.ConversationService;
import com.company.logicstic.modules.messaging.service.MessageService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!nodb")
@RestController
@RequestMapping("/api/messages")
@Validated
public class MessageController {

  private final ConversationService conversationService;
  private final MessageService messageService;

  public MessageController(ConversationService conversationService, MessageService messageService) {
    this.conversationService = conversationService;
    this.messageService = messageService;
  }

  // Conversations

  @GetMapping("/conversations")
  public ResponseEntity<ApiResponse<PagedResponse<ConversationResponse>>> listConversations(
      @RequestParam UUID employeeId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      HttpServletRequest request) {
    PagedResponse<ConversationResponse> data =
        conversationService.listByParticipant(employeeId, page, pageSize);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/conversations/{id}")
  public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
      @PathVariable UUID id, HttpServletRequest request) {
    ConversationResponse data = conversationService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping("/conversations")
  public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
      @Valid @RequestBody CreateConversationRequest body, HttpServletRequest request) {
    ConversationResponse data = conversationService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  // Messages

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> listMessages(
      @RequestParam UUID conversationId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize,
      HttpServletRequest request) {
    PagedResponse<MessageResponse> data =
        messageService.listByConversation(conversationId, page, pageSize);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
      @Valid @RequestBody SendMessageRequest body, HttpServletRequest request) {
    MessageResponse data = messageService.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  // Unread count

  @GetMapping("/unread-count")
  public ResponseEntity<ApiResponse<Long>> getUnreadCount(
      @RequestParam UUID employeeId, HttpServletRequest request) {
    long count = messageService.countUnread(employeeId);
    return ResponseEntity.ok(ApiResponse.success("OK", "Unread count retrieved", count, request));
  }

  @PostMapping("/conversations/{conversationId}/read")
  public ResponseEntity<ApiResponse<Integer>> markRead(
      @PathVariable UUID conversationId,
      @RequestParam UUID employeeId,
      HttpServletRequest request) {
    int marked = messageService.markRead(conversationId, employeeId);
    return ResponseEntity.ok(
        ApiResponse.success("OK", "Conversation marked as read", marked, request));
  }
}
