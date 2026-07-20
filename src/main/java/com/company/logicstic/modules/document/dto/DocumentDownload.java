package com.company.logicstic.modules.document.dto;

public record DocumentDownload(String fileName, String contentType, byte[] content) {

  public DocumentDownload {
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
