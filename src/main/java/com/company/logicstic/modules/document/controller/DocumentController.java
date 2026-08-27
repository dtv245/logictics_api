package com.company.logicstic.modules.document.controller;

import com.company.logicstic.modules.document.dto.request.DocumentUploadRequest;
import com.company.logicstic.modules.document.dto.response.DocumentDownloadResponse;
import com.company.logicstic.modules.document.dto.response.DocumentResponse;
import com.company.logicstic.modules.document.service.DocumentService;
import com.company.logicstic.modules.identity.service.CurrentUserService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Profile("!nodb")
@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {

  private final DocumentService documentService;
  private final CurrentUserService currentUserService;

  public DocumentController(
      DocumentService documentService, CurrentUserService currentUserService) {
    this.documentService = documentService;
    this.currentUserService = currentUserService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> search(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID loadId,
      @RequestParam(required = false) UUID truckId,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestParam(defaultValue = "fileName") String orderBy,
      @RequestParam(defaultValue = "false") boolean descending,
      HttpServletRequest request) {
    PagedResponse<DocumentResponse> data =
        documentService.search(
            type, status, loadId, truckId, employeeId, page, pageSize, orderBy, descending);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DocumentResponse>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    DocumentResponse data = documentService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
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

  @GetMapping("/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable UUID id) {
    DocumentDownloadResponse download = documentService.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(download.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + download.fileName().replace("\"", "") + "\"")
        .body(download.content());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID id, HttpServletRequest request) {
    documentService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, request));
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
