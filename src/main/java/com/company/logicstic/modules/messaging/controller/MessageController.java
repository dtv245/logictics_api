package com.company.logicstic.modules.messaging.controller;

import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.dto.response.ConversationResponse;
import com.company.logicstic.modules.messaging.dto.response.MessageResponse;
import com.company.logicstic.modules.messaging.service.ConversationService;
import com.company.logicstic.modules.messaging.service.MessageService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
  private final CurrentUserService currentUserService;

  public MessageController(
      ConversationService conversationService,
      MessageService messageService,
      CurrentUserService currentUserService) {
    this.conversationService = conversationService;
    this.messageService = messageService;
    this.currentUserService = currentUserService;
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
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = requireMatchingEmployee(authentication, employeeId);
    PagedResponse<ConversationResponse> data =
        conversationService.listByParticipant(currentEmployeeId, page, pageSize);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/conversations/{id}")
  public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
      @PathVariable UUID id, JwtAuthenticationToken authentication, HttpServletRequest request) {
    UUID currentEmployeeId = currentUserService.requireCurrentEmployeeId(authentication);
    ConversationResponse data = conversationService.getByIdForParticipant(id, currentEmployeeId);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping("/conversations")
  public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
      @Valid @RequestBody CreateConversationRequest body,
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = currentUserService.requireCurrentEmployeeId(authentication);
    ConversationResponse data = conversationService.createForParticipant(body, currentEmployeeId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  // Messages

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> listMessages(
      @RequestParam UUID conversationId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize,
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = currentUserService.requireCurrentEmployeeId(authentication);
    PagedResponse<MessageResponse> data =
        messageService.listByConversationForParticipant(
            conversationId, currentEmployeeId, page, pageSize);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
      @Valid @RequestBody SendMessageRequest body,
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = requireMatchingEmployee(authentication, body.senderId());
    MessageResponse data = messageService.sendAsParticipant(currentEmployeeId, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  // Unread count

  @GetMapping("/unread-count")
  public ResponseEntity<ApiResponse<Long>> getUnreadCount(
      @RequestParam UUID employeeId,
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = requireMatchingEmployee(authentication, employeeId);
    long count = messageService.countUnread(currentEmployeeId);
    return ResponseEntity.ok(ApiResponse.success("OK", "Unread count retrieved", count, request));
  }

  @PostMapping("/conversations/{conversationId}/read")
  public ResponseEntity<ApiResponse<Integer>> markRead(
      @PathVariable UUID conversationId,
      @RequestParam UUID employeeId,
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = requireMatchingEmployee(authentication, employeeId);
    int marked = messageService.markRead(conversationId, currentEmployeeId);
    return ResponseEntity.ok(
        ApiResponse.success("OK", "Conversation marked as read", marked, request));
  }

  private UUID requireMatchingEmployee(
      JwtAuthenticationToken authentication, UUID assertedEmployeeId) {
    UUID currentEmployeeId = currentUserService.requireCurrentEmployeeId(authentication);
    if (!currentEmployeeId.equals(assertedEmployeeId)) {
      throw new ApiException(
          ErrorCode.ACCESS_DENIED, "Employee identity does not match the authenticated employee");
    }
    return currentEmployeeId;
  }
}
