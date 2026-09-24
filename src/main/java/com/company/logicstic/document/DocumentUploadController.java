package com.company.logicstic.document;

import com.company.logicstic.identity.currentuser.CurrentUserService;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import com.company.logicstic.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload mapping is absent unless its server-side security policy flag is explicitly enabled. */
@Profile("!nodb")
@ConditionalOnProperty(name = "app.documents.upload.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

  private final DocumentService documentService;
  private final CurrentUserService currentUserService;

  public DocumentUploadController(
      DocumentService documentService, CurrentUserService currentUserService) {
    this.documentService = documentService;
    this.currentUserService = currentUserService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<DocumentResponse>> upload(
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("metadata") DocumentUploadRequest metadata,
      JwtAuthenticationToken authentication,
      HttpServletRequest request) {
    UUID currentEmployeeId = requireMatchingUploader(authentication, metadata.uploadedById());
    DocumentResponse data = documentService.upload(currentEmployeeId, file, metadata);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
  }

  private UUID requireMatchingUploader(
      JwtAuthenticationToken authentication, UUID assertedUploaderId) {
    UUID currentEmployeeId = currentUserService.requireCurrentEmployeeId(authentication);
    if (!currentEmployeeId.equals(assertedUploaderId)) {
      throw new ApiException(
          ErrorCode.ACCESS_DENIED, "Document uploader does not match the authenticated employee");
    }
    return currentEmployeeId;
  }
}
