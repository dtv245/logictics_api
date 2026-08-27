package com.company.logicstic.modules.document.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.modules.document.dto.request.DocumentUploadRequest;
import com.company.logicstic.modules.document.dto.response.DocumentResponse;
import com.company.logicstic.modules.document.service.DocumentService;
import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.shared.config.RestAccessDeniedHandler;
import com.company.logicstic.shared.config.RestAuthenticationEntryPoint;
import com.company.logicstic.shared.config.SecurityConfiguration;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import com.company.logicstic.shared.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(DocumentController.class)
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
class DocumentControllerTest {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @MockitoBean private DocumentService documentService;
  @MockitoBean private CurrentUserService currentUserService;

  DocumentControllerTest(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  @Test
  void unauthenticatedUploadNeverInvokesIdentityOrDocumentServices() throws Exception {
    UUID uploaderId = UUID.randomUUID();

    mockMvc
        .perform(uploadRequest(uploadMetadata(uploaderId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    verifyNoInteractions(currentUserService, documentService);
  }

  @Test
  void unmappedIdentityIsForbiddenWithoutDocumentAccess() throws Exception {
    UUID uploaderId = UUID.randomUUID();
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willThrow(
            new ApiException(
                ErrorCode.ACCESS_DENIED,
                "Authenticated identity is not linked to a tenant employee"));

    mockMvc
        .perform(uploadRequest(uploadMetadata(uploaderId)).with(employeeJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(documentService);
  }

  @Test
  void mismatchedUploaderIsForbiddenBeforeDocumentAccess() throws Exception {
    UUID currentEmployeeId = UUID.randomUUID();
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(currentEmployeeId);

    mockMvc
        .perform(uploadRequest(uploadMetadata(UUID.randomUUID())).with(employeeJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(documentService);
  }

  @Test
  void matchingUploaderIsCreatedAndForwardedWithAuthenticatedEmployee() throws Exception {
    UUID currentEmployeeId = UUID.randomUUID();
    DocumentUploadRequest metadata = uploadMetadata(currentEmployeeId);
    DocumentResponse response = responseFor(currentEmployeeId);
    given(currentUserService.requireCurrentEmployeeId(any(JwtAuthenticationToken.class)))
        .willReturn(currentEmployeeId);
    given(documentService.upload(eq(currentEmployeeId), any(MultipartFile.class), eq(metadata)))
        .willReturn(response);

    mockMvc
        .perform(uploadRequest(metadata).with(employeeJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.uploadedById").value(currentEmployeeId.toString()));

    ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
    verify(documentService).upload(eq(currentEmployeeId), fileCaptor.capture(), eq(metadata));
    assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("pod.pdf");
    assertThat(fileCaptor.getValue().getBytes())
        .isEqualTo("proof-of-delivery".getBytes(StandardCharsets.UTF_8));
  }

  private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
      uploadRequest(DocumentUploadRequest metadata) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "pod.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "proof-of-delivery".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile metadataPart =
        new MockMultipartFile(
            "metadata",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(metadata));
    return multipart("/api/documents").file(file).file(metadataPart);
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

  private static DocumentUploadRequest uploadMetadata(UUID uploaderId) {
    return new DocumentUploadRequest(
        "load",
        "pod",
        "Proof of delivery",
        uploaderId,
        UUID.randomUUID(),
        null,
        null,
        "Recipient",
        null,
        null,
        null,
        null);
  }

  private static DocumentResponse responseFor(UUID uploaderId) {
    return new DocumentResponse(
        UUID.randomUUID(),
        "load",
        "stored.pdf",
        "pod.pdf",
        MediaType.APPLICATION_PDF_VALUE,
        17L,
        uploaderId + "/stored.pdf",
        "documents",
        "pod",
        "active",
        "Proof of delivery",
        uploaderId,
        "Test Driver",
        UUID.randomUUID(),
        null,
        null,
        "Recipient",
        null,
        null,
        null,
        null);
  }
}
