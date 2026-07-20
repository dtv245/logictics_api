package com.company.logicstic.modules.document.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!nodb")
public class FileSystemDocumentStorage implements DocumentStorage {

  private final Path root;

  public FileSystemDocumentStorage(
      @Value("${app.documents.storage-root:./data/documents}") String storageRoot) {
    this.root = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public void store(String container, String path, byte[] content) {
    Path target = resolve(container, path);
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, content, StandardOpenOption.CREATE_NEW);
    } catch (IOException exception) {
      throw new DocumentStorageException("Unable to store document", exception);
    }
  }

  @Override
  public byte[] read(String container, String path) {
    try {
      return Files.readAllBytes(resolve(container, path));
    } catch (IOException exception) {
      throw new DocumentStorageException("Unable to read document", exception);
    }
  }

  @Override
  public void delete(String container, String path) {
    try {
      Files.deleteIfExists(resolve(container, path));
    } catch (IOException exception) {
      throw new DocumentStorageException("Unable to delete document", exception);
    }
  }

  private Path resolve(String container, String path) {
    Path resolved = root.resolve(container).resolve(path).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Document path escapes storage root");
    }
    return resolved;
  }
}
