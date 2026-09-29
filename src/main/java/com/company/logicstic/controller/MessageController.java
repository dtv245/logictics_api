package com.company.logicstic.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.message.ConversationView;
import com.company.logicstic.dto.message.CreateConversationRequest;
import com.company.logicstic.dto.message.MessageView;
import com.company.logicstic.dto.message.SendMessageRequest;
import com.company.logicstic.service.ConversationService;
import com.company.logicstic.service.MessageService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public MessageController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    // Conversations

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<PagedResponse<ConversationView>>> listConversations(
            @RequestParam UUID employeeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        PagedResponse<ConversationView> data = conversationService.listByParticipant(employeeId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<ConversationView>> getConversation(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        ConversationView data = conversationService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationView>> createConversation(
            @Valid @RequestBody CreateConversationRequest body,
            HttpServletRequest request
    ) {
        ConversationView data = conversationService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    // Messages

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MessageView>>> listMessages(
            @RequestParam UUID conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest request
    ) {
        PagedResponse<MessageView> data = messageService.listByConversation(conversationId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageView>> sendMessage(
            @Valid @RequestBody SendMessageRequest body,
            HttpServletRequest request
    ) {
        MessageView data = messageService.send(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    // Unread count

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @RequestParam UUID employeeId,
            HttpServletRequest request
    ) {
        long count = messageService.countUnread(employeeId);
        return ResponseEntity.ok(ApiResponse.success("OK", "Unread count retrieved", count, request));
    }
}