package com.company.logicstic.modules.document.service;

import com.company.logicstic.modules.document.dto.DocumentDownload;
import com.company.logicstic.modules.document.dto.DocumentUploadRequest;
import com.company.logicstic.modules.document.dto.DocumentView;
import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.modules.document.mapper.DocumentMapper;
import com.company.logicstic.modules.document.repository.DocumentRepository;
import com.company.logicstic.modules.document.storage.DocumentStorage;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.BadRequestException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class DocumentService extends AbstractBaseService<Document, DocumentView, Void> {

  private final DocumentRepository documentRepository;
  private final DocumentMapper documentMapper;
  private final DocumentStorage documentStorage;
  private final EmployeeRepository employeeRepository;
  private final LoadRepository loadRepository;
  private final TruckRepository truckRepository;

  private static final String BLOB_CONTAINER = "documents";

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentMapper documentMapper,
      DocumentStorage documentStorage,
      EmployeeRepository employeeRepository,
      LoadRepository loadRepository,
      TruckRepository truckRepository) {
    super(documentRepository, documentMapper::toView, null, null);
    this.documentRepository = documentRepository;
    this.documentMapper = documentMapper;
    this.documentStorage = documentStorage;
    this.employeeRepository = employeeRepository;
    this.loadRepository = loadRepository;
    this.truckRepository = truckRepository;
  }

  @Override
  protected String entityName() {
    return "Document";
  }

  public PagedResponse<DocumentView> search(
      String type,
      String status,
      UUID loadId,
      UUID truckId,
      UUID employeeId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(
        documentRepository.search(type, status, loadId, truckId, employeeId, pageable));
  }

  @Transactional
  public DocumentView upload(MultipartFile file, DocumentUploadRequest request) {
    if (file.isEmpty()) {
      throw new BadRequestException("Document file must not be empty");
    }
    String originalName = safeOriginalName(file.getOriginalFilename());
    String storedName = UUID.randomUUID() + extension(originalName);
    String blobPath = request.uploadedById() + "/" + storedName;

    try {
      documentStorage.store(BLOB_CONTAINER, blobPath, file.getBytes());
    } catch (IOException exception) {
      throw new BadRequestException("Unable to read uploaded document");
    }

    try {
      Document document = buildDocument(file, request, originalName, storedName, blobPath);
      return documentMapper.toView(documentRepository.saveAndFlush(document));
    } catch (RuntimeException exception) {
      documentStorage.delete(BLOB_CONTAINER, blobPath);
      throw exception;
    }
  }

  public DocumentDownload download(UUID id) {
    Document document = findDocument(id);
    return new DocumentDownload(
        document.getOriginalFileName(),
        document.getContentType(),
        documentStorage.read(document.getBlobContainer(), document.getBlobPath()));
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    Document document = findDocument(id);
    documentRepository.delete(document);
    documentRepository.flush();
    documentStorage.delete(document.getBlobContainer(), document.getBlobPath());
  }

  private Document buildDocument(
      MultipartFile file,
      DocumentUploadRequest request,
      String originalName,
      String storedName,
      String blobPath) {
    Document document = new Document();
    document.setOwnerType(request.ownerType());
    document.setFileName(storedName);
    document.setOriginalFileName(originalName);
    document.setContentType(
        file.getContentType() != null ? file.getContentType() : "application/octet-stream");
    document.setFileSizeBytes(file.getSize());
    document.setBlobPath(blobPath);
    document.setBlobContainer(BLOB_CONTAINER);
    document.setType(request.type());
    document.setStatus("active");
    document.setDescription(request.description());
    document.setRecipientName(request.recipientName());
    document.setCapturedAt(request.capturedAt());
    document.setCaptureLatitude(request.captureLatitude());
    document.setCaptureLongitude(request.captureLongitude());
    document.setNotes(request.notes());
    document.setUploadedBy(
        employeeRepository
            .findById(request.uploadedById())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Employee not found: " + request.uploadedById())));
    if (request.employeeId() != null) {
      document.setEmployee(
          employeeRepository
              .findById(request.employeeId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Employee not found: " + request.employeeId())));
    }
    if (request.loadId() != null) {
      document.setLoad(
          loadRepository
              .findById(request.loadId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Load not found: " + request.loadId())));
    }
    if (request.truckId() != null) {
      document.setTruck(
          truckRepository
              .findById(request.truckId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Truck not found: " + request.truckId())));
    }
    return document;
  }

  private Document findDocument(UUID id) {
    return documentRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
  }

  private String safeOriginalName(String originalName) {
    if (originalName == null || originalName.isBlank()) {
      throw new BadRequestException("Document file name must not be blank");
    }
    String safeName = Path.of(originalName).getFileName().toString();
    if (!safeName.equals(originalName) || safeName.contains("..")) {
      throw new BadRequestException("Invalid document file name");
    }
    return safeName;
  }

  private String extension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index > 0 ? fileName.substring(index) : "";
  }
}
