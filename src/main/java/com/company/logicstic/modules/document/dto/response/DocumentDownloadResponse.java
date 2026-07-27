package com.company.logicstic.modules.document.dto.response;

public record DocumentDownloadResponse(String fileName, String contentType, byte[] content) {

  public DocumentDownloadResponse {
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
