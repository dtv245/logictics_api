package com.company.logicstic.modules.messaging.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.dto.response.ConversationResponse;
import com.company.logicstic.modules.messaging.dto.response.MessageResponse;
import com.company.logicstic.modules.messaging.service.ConversationService;
import com.company.logicstic.modules.messaging.service.MessageService;
import com.company.logicstic.shared.config.RestAccessDeniedHandler;
import com.company.logicstic.shared.config.RestAuthenticationEntryPoint;
import com.company.logicstic.shared.config.SecurityConfiguration;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import com.company.logicstic.shared.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(MessageController.class)
@ImportAutoConfiguration({
  SecurityAutoConfiguration.class,
  ServletWebSecurityAutoConfiguration.class
})
@Import({
  SecurityConfiguration.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.security.jwt.issuer=https://issuer.example",
      "app.security.jwt.audience=logisticsx.api",
      "app.security.jwt.jwk-set-uri=https://issuer.example/jwks"
    })
class MessageControllerTest {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @MockitoBean private ConversationService conversationService;
  @MockitoBean private MessageService messageService;
  @MockitoBean private CurrentUserService currentUserService;

  MessageControllerTest(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  @Test
  void matchingLegacyEmployeeListsOnlyAuthenticatedEmployeesConversations() throws Exception {
    UUID employeeId = UUID.randomUUID();
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(employeeId);
    given(conversationService.listByParticipant(employeeId, 1, 20))
        .willReturn(new PagedResponse<>(List.of(), 0, 0, 1, 20));

    mockMvc
        .perform(
            get("/api/messages/conversations")
                .param("employeeId", employeeId.toString())
                .with(employeeJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentPage").value(1));

    verify(conversationService).listByParticipant(employeeId, 1, 20);
  }

  @Test
  void mismatchedLegacyEmployeeIsForbiddenBeforeConversationAccess() throws Exception {
    UUID currentEmployeeId = UUID.randomUUID();
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(currentEmployeeId);

    mockMvc
        .perform(
            get("/api/messages/conversations")
                .param("employeeId", UUID.randomUUID().toString())
                .with(employeeJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(conversationService, messageService);
  }

  @Test
  void conversationDetailUsesParticipantScopedService() throws Exception {
    UUID currentEmployeeId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(currentEmployeeId);
    given(conversationService.getByIdForParticipant(conversationId, currentEmployeeId))
        .willReturn(conversation(conversationId, currentEmployeeId));

    mockMvc
        .perform(get("/api/messages/conversations/{id}", conversationId).with(employeeJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(conversationId.toString()));

    verify(conversationService).getByIdForParticipant(conversationId, currentEmployeeId);
    verify(conversationService, never()).getById(conversationId);
  }

  @Test
  void sendDelegatesOnlyToPrincipalBoundService() throws Exception {
    UUID currentEmployeeId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    SendMessageRequest request = new SendMessageRequest(conversationId, currentEmployeeId, "hello");
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(currentEmployeeId);
    given(messageService.sendAsParticipant(currentEmployeeId, request))
        .willReturn(message(conversationId, currentEmployeeId));

    mockMvc
        .perform(
            post("/api/messages")
                .with(employeeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.senderId").value(currentEmployeeId.toString()));

    verify(messageService).sendAsParticipant(currentEmployeeId, request);
    verify(messageService, never()).create(request);
  }

  @Test
  void mismatchedLegacySenderIsForbiddenBeforeMessageAccess() throws Exception {
    UUID currentEmployeeId = UUID.randomUUID();
    SendMessageRequest request =
        new SendMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "forged");
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(currentEmployeeId);

    mockMvc
        .perform(
            post("/api/messages")
                .with(employeeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(conversationService, messageService);
  }

  @Test
  void unmappedIdentityIsForbiddenWithoutMessagingAccess() throws Exception {
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willThrow(
            new ApiException(
                ErrorCode.ACCESS_DENIED,
                "Authenticated identity is not linked to a tenant employee"));

    mockMvc
        .perform(
            get("/api/messages/unread-count")
                .param("employeeId", UUID.randomUUID().toString())
                .with(employeeJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(conversationService, messageService);
  }

  @Test
  void unauthenticatedRequestNeverInvokesIdentityOrMessagingServices() throws Exception {
    mockMvc
        .perform(
            get("/api/messages/unread-count").param("employeeId", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    verifyNoInteractions(currentUserService, conversationService, messageService);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor employeeJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .subject("employee-subject")
                    .claim("email", "employee@example.com")
                    .claim("tenant", "tenant-a"))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private static ConversationResponse conversation(UUID conversationId, UUID employeeId) {
    OffsetDateTime now = OffsetDateTime.now();
    return new ConversationResponse(
        conversationId, "Dispatch", null, false, now, now, List.of(employeeId));
  }

  private static MessageResponse message(UUID conversationId, UUID employeeId) {
    return new MessageResponse(
        UUID.randomUUID(),
        conversationId,
        employeeId,
        "Test Driver",
        "hello",
        OffsetDateTime.now(),
        false);
  }
}
