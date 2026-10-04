package com.company.logicstic.integration.lark.base;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class LarkBaseClient {

  private static final Logger log = LoggerFactory.getLogger(LarkBaseClient.class);

  private final LarkProperties properties;
  private final LarkBaseTokenProvider tokenProvider;
  private final RestTemplate restTemplate;

  public LarkBaseClient(
      LarkProperties properties,
      LarkBaseTokenProvider tokenProvider,
      RestTemplate restTemplate) {
    this.properties = properties;
    this.tokenProvider = tokenProvider;
    this.restTemplate = restTemplate;
  }

  public LarkBaseRecord createRecord(String appToken, String tableId, LarkBaseRecord record) {
    validateParams(appToken, tableId);
    String base = properties.baseUrl().replaceAll("/+$", "");
    String url = base + "/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records";

    HttpHeaders headers = createAuthorizedHeaders();
    Map<String, Object> payload = Map.of("fields", record.fields());
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

    try {
      ResponseEntity<LarkRecordResponse> response =
          restTemplate.exchange(url, HttpMethod.POST, entity, LarkRecordResponse.class);
      LarkRecordResponse body = response.getBody();

      if (body == null || body.code() != 0 || body.data() == null) {
        String msg = body != null ? body.msg() : "empty response";
        log.error("Failed to create Lark Bitable record: code={}, msg={}", body != null ? body.code() : -1, msg);
        throw new ApiException(
            HttpStatus.BAD_GATEWAY, "LARK_BASE_ERROR", "Failed to create record in Lark Base: " + msg);
      }

      return body.data().record();
    } catch (RestClientException ex) {
      log.error("Error creating record in Lark Base: {}", ex.getMessage());
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "LARK_BASE_ERROR", "Network error when creating Lark Base record: " + ex.getMessage());
    }
  }

  public LarkBaseRecord updateRecord(String appToken, String tableId, String recordId, LarkBaseRecord record) {
    validateParams(appToken, tableId);
    if (!StringUtils.hasText(recordId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "recordId must not be blank for update");
    }

    String base = properties.baseUrl().replaceAll("/+$", "");
    String url = base + "/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/" + recordId.trim();

    HttpHeaders headers = createAuthorizedHeaders();
    Map<String, Object> payload = Map.of("fields", record.fields());
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

    try {
      ResponseEntity<LarkRecordResponse> response =
          restTemplate.exchange(url, HttpMethod.PUT, entity, LarkRecordResponse.class);
      LarkRecordResponse body = response.getBody();

      if (body == null || body.code() != 0 || body.data() == null) {
        String msg = body != null ? body.msg() : "empty response";
        log.error("Failed to update Lark Bitable record {}: code={}, msg={}", recordId, body != null ? body.code() : -1, msg);
        throw new ApiException(
            HttpStatus.BAD_GATEWAY, "LARK_BASE_ERROR", "Failed to update record in Lark Base: " + msg);
      }

      return body.data().record();
    } catch (RestClientException ex) {
      log.error("Error updating record in Lark Base {}: {}", recordId, ex.getMessage());
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "LARK_BASE_ERROR", "Network error when updating Lark Base record: " + ex.getMessage());
    }
  }

  public boolean deleteRecord(String appToken, String tableId, String recordId) {
    validateParams(appToken, tableId);
    if (!StringUtils.hasText(recordId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "recordId must not be blank for delete");
    }

    String base = properties.baseUrl().replaceAll("/+$", "");
    String url = base + "/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/" + recordId.trim();

    HttpHeaders headers = createAuthorizedHeaders();
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    try {
      ResponseEntity<LarkDeleteResponse> response =
          restTemplate.exchange(url, HttpMethod.DELETE, entity, LarkDeleteResponse.class);
      LarkDeleteResponse body = response.getBody();
      return body != null && body.code() == 0;
    } catch (RestClientException ex) {
      log.error("Error deleting record in Lark Base {}: {}", recordId, ex.getMessage());
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "LARK_BASE_ERROR", "Network error when deleting Lark Base record: " + ex.getMessage());
    }
  }

  public List<LarkBaseRecord> listRecords(String appToken, String tableId, Integer pageSize, String pageToken) {
    validateParams(appToken, tableId);
    String base = properties.baseUrl().replaceAll("/+$", "");
    StringBuilder urlBuilder = new StringBuilder(base)
        .append("/open-apis/bitable/v1/apps/")
        .append(appToken)
        .append("/tables/")
        .append(tableId)
        .append("/records?");

    if (pageSize != null && pageSize > 0) {
      urlBuilder.append("page_size=").append(pageSize).append("&");
    }
    if (StringUtils.hasText(pageToken)) {
      urlBuilder.append("page_token=").append(pageToken).append("&");
    }

    HttpHeaders headers = createAuthorizedHeaders();
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    try {
      ResponseEntity<LarkRecordListResponse> response =
          restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity, LarkRecordListResponse.class);
      LarkRecordListResponse body = response.getBody();

      if (body != null && body.code() == 0 && body.data() != null && body.data().items() != null) {
        return body.data().items();
      }
      return List.of();
    } catch (RestClientException ex) {
      log.error("Error listing records from Lark Base: {}", ex.getMessage());
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "LARK_BASE_ERROR", "Network error when listing Lark Base records: " + ex.getMessage());
    }
  }

  private HttpHeaders createAuthorizedHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(tokenProvider.getTenantAccessToken());
    return headers;
  }

  private void validateParams(String appToken, String tableId) {
    if (!StringUtils.hasText(appToken)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Lark Base appToken must not be blank");
    }
    if (!StringUtils.hasText(tableId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Lark Base tableId must not be blank");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkRecordResponse(int code, String msg, LarkRecordWrapper data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkRecordWrapper(LarkBaseRecord record) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkRecordListResponse(int code, String msg, LarkRecordListWrapper data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkRecordListWrapper(
      @JsonProperty("has_more") boolean hasMore,
      @JsonProperty("page_token") String pageToken,
      int total,
      List<LarkBaseRecord> items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkDeleteResponse(int code, String msg, Map<String, Object> data) {}
}
