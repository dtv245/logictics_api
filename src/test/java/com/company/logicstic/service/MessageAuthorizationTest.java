package com.company.logicstic.service;

import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.message.SendMessageRequest;
import com.company.logicstic.entity.Conversation;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Message;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.ConversationParticipantRepository;
import com.company.logicstic.repository.ConversationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageAuthorizationTest {

    private MessageRepository messageRepository;
    private ConversationRepository conversationRepository;
    private ConversationParticipantRepository conversationParticipantRepository;
    private EmployeeRepository employeeRepository;
    private CurrentUserService currentUserService;
    private MessageService messageService;

    private UUID currentEmployeeId;
    private UUID otherEmployeeId;
    private UUID conversationId;
    private Conversation conversation;
    private Employee currentEmployee;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        conversationParticipantRepository = mock(ConversationParticipantRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        currentUserService = mock(CurrentUserService.class);

        messageService = new MessageService(
                messageRepository,
                conversationRepository,
                conversationParticipantRepository,
                employeeRepository,
                currentUserService
        );

        currentEmployeeId = UUID.randomUUID();
        otherEmployeeId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setIsTenantChat(false);

        currentEmployee = new Employee();
        currentEmployee.setId(currentEmployeeId);

        CurrentUserResponse currentUser = new CurrentUserResponse(
                "sub-123", "user@test.local", "tenant-1", List.of("DISPATCHER"), currentEmployeeId
        );
        when(currentUserService.current()).thenReturn(currentUser);
        when(currentUserService.requireMappedEmployee()).thenReturn(currentUser);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(employeeRepository.findById(currentEmployeeId)).thenReturn(Optional.of(currentEmployee));
    }

    @Test
    void rejectsMessageWhenSenderIdDoesNotMatchAuthenticatedUser() {
        SendMessageRequest request = new SendMessageRequest(
                conversationId,
                otherEmployeeId, // Attempted spoofing / IDOR
                "Spoofed message"
        );

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> messageService.send(request));
        assertTrue(ex.getMessage().contains("Cannot send message on behalf of another user"));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void rejectsMessageWhenSenderIsNotParticipantInPrivateConversation() {
        SendMessageRequest request = new SendMessageRequest(
                conversationId,
                currentEmployeeId,
                "Unauthorized hello"
        );
        when(conversationParticipantRepository.existsByConversationIdAndEmployeeId(conversationId, currentEmployeeId))
                .thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> messageService.send(request));
        assertTrue(ex.getMessage().contains("not a participant"));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void allowsMessageWhenSenderIsParticipant() {
        SendMessageRequest request = new SendMessageRequest(
                conversationId,
                currentEmployeeId,
                "Legit hello"
        );
        when(conversationParticipantRepository.existsByConversationIdAndEmployeeId(conversationId, currentEmployeeId))
                .thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        var result = messageService.send(request);
        assertNotNull(result);
        assertEquals("Legit hello", result.content());
        assertEquals(currentEmployeeId, result.senderId());
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void allowsMessageWithoutSenderIdWhenAuthenticatedUserIsParticipant() {
        // senderId omitted by client
        SendMessageRequest request = new SendMessageRequest(
                conversationId,
                null,
                "Sender ID omitted"
        );
        when(conversationParticipantRepository.existsByConversationIdAndEmployeeId(conversationId, currentEmployeeId))
                .thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        var result = messageService.send(request);
        assertNotNull(result);
        assertEquals(currentEmployeeId, result.senderId());
    }

    @Test
    void rejectsListMessagesWhenNotParticipant() {
        when(conversationParticipantRepository.existsByConversationIdAndEmployeeId(conversationId, currentEmployeeId))
                .thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                messageService.listByConversation(conversationId, 1, 10));
        assertTrue(ex.getMessage().contains("not a participant"));
    }

    @Test
    void rejectsUnreadCountForDifferentUser() {
        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                messageService.countUnread(otherEmployeeId));
        assertTrue(ex.getMessage().contains("Cannot access unread count of another user"));
    }

    @Test void unmappedEmployeeCannotReadSendOrSelectAnotherEmployeesUnreadCount() {
        when(currentUserService.requireMappedEmployee()).thenThrow(new ForbiddenException("Employee profile required"));
        assertThrows(ForbiddenException.class, () -> messageService.listByConversation(conversationId, 1, 10));
        assertThrows(ForbiddenException.class, () -> messageService.send(new SendMessageRequest(conversationId, null, "denied")));
        assertThrows(ForbiddenException.class, () -> messageService.countUnread(otherEmployeeId));
        verifyNoInteractions(messageRepository, conversationRepository, conversationParticipantRepository);
    }
}
