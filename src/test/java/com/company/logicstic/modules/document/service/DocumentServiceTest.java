package com.company.logicstic.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.document.dto.request.DocumentUploadRequest;
import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.modules.document.mapper.DocumentMapper;
import com.company.logicstic.modules.document.repository.DocumentRepository;
import com.company.logicstic.modules.document.service.impl.DocumentServiceImpl;
import com.company.logicstic.modules.document.storage.DocumentStorage;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.BadRequestException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentServiceTest {

  @Test
  void uploadDownloadAndDeleteKeepsBlobAndMetadataLifecycleTogether() {
    UUID uploaderId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Employee uploader = new Employee();
    uploader.setId(uploaderId);
    uploader.setFirstName("Upload");
    uploader.setLastName("User");
    AtomicReference<Document> storedDocument = new AtomicReference<>();
    InMemoryStorage storage = new InMemoryStorage();
    DocumentRepository repository =
        proxy(
            DocumentRepository.class,
            (method, args) -> {
              if (method.equals("saveAndFlush")) {
                Document document = (Document) args[0];
                document.setId(documentId);
                storedDocument.set(document);
                return document;
              }
              if (method.equals("findById")) {
                return Optional.ofNullable(storedDocument.get());
              }
              if (method.equals("delete")) {
                return null;
              }
              if (method.equals("flush")) {
                return null;
              }
              throw new AssertionError("Unexpected DocumentRepository call: " + method);
            });
    DocumentService service = service(repository, storage, uploaderId, uploader);
    byte[] content = "proof-of-delivery".getBytes();

    var view =
        service.upload(
            uploaderId,
            new MockMultipartFile("file", "proof.pdf", "application/pdf", content),
            request(uploaderId));

    assertThat(view.id()).isEqualTo(documentId);
    assertThat(storedDocument.get().getUploadedBy()).isSameAs(uploader);
    assertThat(storedDocument.get().getBlobPath()).startsWith(uploaderId + "/");
    assertThat(storage.files).containsKey("documents/" + storedDocument.get().getBlobPath());
    assertThat(storage.files).hasSize(1);
    assertThat(service.download(documentId).content()).isEqualTo(content);

    service.delete(documentId);
    assertThat(storage.files).isEmpty();
  }

  @Test
  void uploadDeletesBlobWhenMetadataSaveFails() {
    UUID uploaderId = UUID.randomUUID();
    Employee uploader = new Employee();
    uploader.setId(uploaderId);
    InMemoryStorage storage = new InMemoryStorage();
    DocumentRepository repository =
        proxy(
            DocumentRepository.class,
            (method, args) -> {
              if (method.equals("saveAndFlush")) {
                throw new IllegalStateException("database unavailable");
              }
              throw new AssertionError("Unexpected DocumentRepository call: " + method);
            });
    DocumentService service = service(repository, storage, uploaderId, uploader);

    assertThatThrownBy(
            () ->
                service.upload(
                    uploaderId,
                    new MockMultipartFile("file", "proof.pdf", "application/pdf", "x".getBytes()),
                    request(uploaderId)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(storage.files).isEmpty();
  }

  @Test
  void uploadRejectsForgedUploaderBeforeTouchingFileOrDependencies() {
    UUID currentEmployeeId = UUID.randomUUID();
    UUID claimedUploaderId = UUID.randomUUID();
    DocumentService service =
        new DocumentServiceImpl(
            unused(DocumentRepository.class),
            Mappers.getMapper(DocumentMapper.class),
            unused(DocumentStorage.class),
            unused(EmployeeService.class),
            unused(LoadService.class),
            unused(TruckService.class));

    assertThatThrownBy(
            () ->
                service.upload(
                    currentEmployeeId, unused(MultipartFile.class), request(claimedUploaderId)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("ACCESS_DENIED"));
  }

  @Test
  void uploadRejectsUnsafeFileNameVariantsBeforeResolvingDependenciesOrWritingBlob() {
    UUID uploaderId = UUID.randomUUID();
    String[] unsafeNames = {
      "",
      "   ",
      "folder/proof.pdf",
      "folder\\proof.pdf",
      "proof..pdf",
      "proof\r\nInjected.pdf",
      "proof" + (char) 0x00 + ".pdf",
      "proof" + (char) 0x1F + ".pdf",
      "proof" + (char) 0x7F + ".pdf"
    };

    for (String unsafeName : unsafeNames) {
      InMemoryStorage storage = new InMemoryStorage();
      DocumentService service =
          new DocumentServiceImpl(
              unused(DocumentRepository.class),
              Mappers.getMapper(DocumentMapper.class),
              storage,
              unused(EmployeeService.class),
              unused(LoadService.class),
              unused(TruckService.class));
      MockMultipartFile file =
          new MockMultipartFile("file", unsafeName, "application/pdf", "x".getBytes());

      assertThatThrownBy(() -> service.upload(uploaderId, file, request(uploaderId)))
          .isInstanceOf(BadRequestException.class);
      assertThat(storage.files).isEmpty();
    }
  }

  @Test
  void uploadRejectsEmptyFileBeforeResolvingDependenciesOrWritingBlob() {
    UUID uploaderId = UUID.randomUUID();
    InMemoryStorage storage = new InMemoryStorage();
    DocumentService service =
        new DocumentServiceImpl(
            unused(DocumentRepository.class),
            Mappers.getMapper(DocumentMapper.class),
            storage,
            unused(EmployeeService.class),
            unused(LoadService.class),
            unused(TruckService.class));

    assertThatThrownBy(
            () ->
                service.upload(
                    uploaderId,
                    new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[0]),
                    request(uploaderId)))
        .isInstanceOf(BadRequestException.class);
    assertThat(storage.files).isEmpty();
  }

  private DocumentService service(
      DocumentRepository repository, DocumentStorage storage, UUID uploaderId, Employee uploader) {
    EmployeeService employees =
        proxy(
            EmployeeService.class,
            (method, args) -> {
              if (method.equals("getEntityById") && args[0].equals(uploaderId)) {
                return uploader;
              }
              throw new AssertionError("Unexpected EmployeeService call: " + method);
            });
    return new DocumentServiceImpl(
        repository,
        Mappers.getMapper(DocumentMapper.class),
        storage,
        employees,
        unused(LoadService.class),
        unused(TruckService.class));
  }

  private DocumentUploadRequest request(UUID uploaderId) {
    return new DocumentUploadRequest(
        "load",
        "proof-of-delivery",
        null,
        uploaderId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static class InMemoryStorage implements DocumentStorage {
    private final Map<String, byte[]> files = new HashMap<>();

    @Override
    public void store(String container, String path, byte[] content) {
      files.put(container + "/" + path, content.clone());
    }

    @Override
    public byte[] read(String container, String path) {
      return files.get(container + "/" + path).clone();
    }

    @Override
    public void delete(String container, String path) {
      files.remove(container + "/" + path);
    }
  }

  private static <T> T unused(Class<T> type) {
    return proxy(
        type,
        (method, args) -> {
          throw new AssertionError("Unexpected " + type.getSimpleName() + " call: " + method);
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, RepositoryCall call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> call.invoke(method.getName(), args));
  }

  @FunctionalInterface
  private interface RepositoryCall {
    Object invoke(String method, Object[] args);
  }
}
