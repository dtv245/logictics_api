package com.company.logicstic.modules.document.storage;

public interface DocumentStorage {

  void store(String container, String path, byte[] content);

  byte[] read(String container, String path);

  void delete(String container, String path);
}
