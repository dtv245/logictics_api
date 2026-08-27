package com.company.logicstic.modules.document.service;

import com.company.logicstic.modules.document.dto.request.DocumentUploadRequest;
import com.company.logicstic.modules.document.dto.response.DocumentDownloadResponse;
import com.company.logicstic.modules.document.dto.response.DocumentResponse;
import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.shared.dto.PagedResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public API of the document feature — POD, BOL, truck and employee paperwork ({@code
 * docs/docs/business-spec.md} §6.1).
 *
 * <p>This interface deliberately does <em>not</em> extend {@code CrudService}: a document is
 * created by {@link #upload} with its binary payload, never from a plain JSON body, and its
 * metadata is immutable once stored. Declaring {@code create}/{@code update} would advertise
 * operations the feature does not implement.
 */
public interface DocumentService {

  /**
   * Searches documents with optional type, status and owning-entity filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<DocumentResponse> search(
      String type,
      String status,
      UUID loadId,
      UUID truckId,
      UUID employeeId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);

  DocumentResponse getById(UUID id);

  /** Returns the managed entity for another feature that needs it as an association target. */
  Document getEntityById(UUID id);

  /**
   * Stores the uploaded file in blob storage and records its metadata against the referenced load,
   * truck or employee.
   */
  DocumentResponse upload(
      UUID currentEmployeeId, MultipartFile file, DocumentUploadRequest request);

  /** Reads the stored bytes together with the file name and content type. */
  DocumentDownloadResponse download(UUID id);

  /** Deletes the metadata record and the stored blob. */
  void delete(UUID id);
}
